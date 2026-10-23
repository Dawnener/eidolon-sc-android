"""The API for signing in and out, and for the user's account.

Signing in with Google or GitHub goes through the browser:

    /api/auth/{provider}/start     → the provider's sign-in page
    /api/auth/{provider}/callback  ← back from it, with a code and the state sent there

The state is kept in a short-lived cookie of its own, so a callback can only finish a
sign-in this browser started. The callback sends the browser on: home when signed in,
the sign-in page with `?error=<reason>` when not (see `accounts.Refusal`, plus `state`
for a callback this browser did not start or that expired, `cancelled` when the user
turned the provider down, and `provider` when the provider could not be reached).

A signed-in user links another identity the same way, starting with `?link=1`; the
callback then sends the browser back to the sign-in settings (`/settings/sign-in`), with
`?linked=<provider>` or `?error=<reason>`.
"""

import html
import json
import logging
import secrets
import threading
from datetime import datetime, timedelta
from typing import Annotated, Any
from urllib.parse import urlencode

from fastapi import FastAPI, HTTPException, Path, Query, Request, Response
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from pydantic import (
    BaseModel, Field, Secret, StrictBool, StringConstraints, field_validator, model_validator,
)

from eidolon.accounts import SignInRefused, signs_in_as_admin
from eidolon.activity import host
from eidolon.assembly import Backend
from eidolon.config import (
    DEFAULT_FISH_AUDIO_MODEL,
    SERVICES,
    BailianRegion,
    ChatModelSettings,
    FishAudioModel,
    ImageFormat,
    ImageSettings,
    LLMProtocol,
    LLMSettings,
    ReasoningEffort,
    SttKey,
    SttProvider,
    checked_base_url,
    checked_extra_body,
    effort_problem,
)
from eidolon.identity import PROVIDER_NAMES, IdentityError
from eidolon.own_keys import NO_PHOTOS, BroughtKeys, OwnKeyRejected, named_services
from eidolon.price_catalog import describe_offer, describe_provider, describe_thinking
from eidolon.reach_outs import ReachOutsOff
from eidolon.store import LastSignInMethod, NotFound
from eidolon.throttle import Throttle
from eidolon.sessions import (
    SESSION_COOKIE,
    SignedIn,
    UserId,
    clear_session_cookie,
    session_user,
    set_session_cookie,
)

log = logging.getLogger(__name__)

STATE_COOKIE = "eidolon_oauth_state"
STATE_LIFETIME = 600
"""How long, in seconds, the browser has to sign in at the provider and come back."""

MOBILE_REDIRECT = "eidolon://auth"
"""Where the browser sends a phone after signing in there; the app takes the one-off code
from it and exchanges it for the session cookie in its own HTTP client."""

MOBILE_CODE_LIFETIME = 120
"""How long, in seconds, the app has to exchange the one-off code it came back with."""


class MobileExchange(BaseModel):
    """The one-off code from the browser callback, exchanged for a session by the app."""

    code: str = Field(min_length=20, max_length=200)


class DevLogin(BaseModel):
    name: str = Field(min_length=1, max_length=40)


class Deletion(BaseModel):
    """Deleting the user's data needs saying so, not just a request that might be a slip."""

    confirm: bool

    @model_validator(mode="after")
    def _confirmed(self) -> "Deletion":
        if not self.confirm:
            raise ValueError("需要确认才能删除全部数据")
        return self


ApiKey = Secret[
    Annotated[str, StringConstraints(min_length=8, max_length=500, pattern=r"^[\x21-\x7e]+$")]
]
"""A user's own key as a request brings it, never shown in a repr or traceback."""


class OwnLLMListInput(BaseModel):
    """Where the user's LLM is and their key, for the service to list the models it offers
    the key."""

    base_url: str = Field(min_length=1, max_length=500)
    api_key: ApiKey
    protocol: LLMProtocol = "chat-completions"

    _base_url = field_validator("base_url")(checked_base_url)


class OwnLLMInput(BaseModel):
    """A model of the user's own, as their browser keeps it and brings it with each text
    turn and call: its effort and extra body checked here each time, as the admin console
    checks the deployer's when saved, for they are never saved (ADR-0017); left out by
    pages from before, for none."""

    base_url: str = Field(min_length=1, max_length=500)
    api_key: ApiKey
    model: str = Field(min_length=1, max_length=200)
    protocol: LLMProtocol = "chat-completions"
    reasoning_effort: ReasoningEffort | None = None
    extra_body: dict[str, Any] | None = None

    _base_url = field_validator("base_url")(checked_base_url)

    @field_validator("extra_body", mode="before")
    @classmethod
    def _extra_body(cls, value: object) -> dict[str, Any] | None:
        return None if value is None else checked_extra_body(value)

    @model_validator(mode="after")
    def _effort(self) -> "OwnLLMInput":
        _require_writable(self.reasoning_effort, self.protocol)
        return self

    def settings(self) -> LLMSettings:
        """The model as turns run on it: its address and model without surrounding spaces."""
        return LLMSettings(
            self.base_url.strip(), self.api_key.get_secret_value(), self.model.strip(),
            self.protocol, reasoning_effort=self.reasoning_effort, extra_body=self.extra_body,
        )


class OwnChatLLMInput(OwnLLMInput):
    """The user's own LLM playing the characters, at an effort of its own in calls (see
    `ChatModelSettings`)."""

    call_reasoning_effort: ReasoningEffort | None = None

    @model_validator(mode="after")
    def _call_effort(self) -> "OwnChatLLMInput":
        _require_writable(self.call_reasoning_effort, self.protocol)
        return self

    def chat_settings(self) -> ChatModelSettings:
        """Its `settings`, with its effort in calls."""
        return ChatModelSettings(self.settings(), self.call_reasoning_effort)


def _require_writable(effort: ReasoningEffort | None, protocol: LLMProtocol) -> None:
    """Raise ValueError saying why, if `protocol` has no way to write `effort`."""
    if effort is not None and (problem := effort_problem(effort, protocol)):
        raise ValueError(problem)


class OwnTTSInput(BaseModel):
    """The user's own key for speech synthesis (Fish Audio), as their browser keeps it,
    with the model they chose; the free one, kept before they could choose."""

    api_key: ApiKey
    model: FishAudioModel = DEFAULT_FISH_AUDIO_MODEL


class OwnSTTInput(BaseModel):
    """The user's own key for speech recognition, as their browser keeps it: for the
    service it is for, with the region a DashScope key works in, as its try found."""

    api_key: ApiKey
    provider: SttProvider = "soniox"
    region: BailianRegion | None = None

    def key(self) -> SttKey:
        """As calls hear on it; a region only for DashScope."""
        region = self.region if self.provider == "bailian" else None
        return SttKey(self.provider, self.api_key.get_secret_value(), region)


class OwnImageInput(BaseModel):
    """The user's own image generation, as their browser keeps it: in which format, where,
    with which key and model; how many at a time and the art style are not theirs to set."""

    api_format: ImageFormat
    base_url: str = Field(min_length=1, max_length=500)
    api_key: ApiKey
    model: str = Field(min_length=1, max_length=200)

    def settings(self) -> ImageSettings:
        """As her photos are generated on it: its address without surrounding spaces or a
        trailing slash, as the admin console keeps the deployer's, and its model without
        surrounding spaces."""
        return ImageSettings(
            self.api_key.get_secret_value(), self.base_url.strip().rstrip("/"),
            self.model.strip(), self.api_format,
        )


class OwnKeysInput(BaseModel):
    """The user's own keys a turn or a call brings, by service; each may be left out. Their
    own model for memories (`memory_llm`) only with their own LLM."""

    llm: OwnChatLLMInput | None = None
    tts: OwnTTSInput | None = None
    stt: OwnSTTInput | None = None
    memory_llm: OwnLLMInput | None = None
    image: OwnImageInput | None = None

    @model_validator(mode="after")
    def _memory_llm_with_llm(self) -> "OwnKeysInput":
        if self.memory_llm is not None and self.llm is None:
            raise ValueError("后台模型要和自带的 LLM 一起带上")
        return self

    def brought(self) -> BroughtKeys:
        return BroughtKeys(
            self.llm.chat_settings() if self.llm else None,
            self.tts.api_key.get_secret_value() if self.tts else None,
            self.stt.key() if self.stt else None,
            self.memory_llm.settings() if self.memory_llm else None,
            self.tts.model if self.tts else DEFAULT_FISH_AUDIO_MODEL,
            self.image.settings() if self.image else None,
        )


class ReachOutSwitch(BaseModel):
    """The user's own switch for reach-outs (GLOSSARY.md「搭话」)."""

    on: StrictBool


class Consent(BaseModel):
    """Agreeing to the privacy policy and terms of use, and saying they are 13 or older."""

    agree_to_terms: bool
    at_least_13: bool

    @model_validator(mode="after")
    def _both(self) -> "Consent":
        if not self.agree_to_terms:
            raise ValueError("需要同意隐私政策和使用条款")
        if not self.at_least_13:
            raise ValueError("需要年满 13 岁才能使用")
        return self


def add_account_routes(app: FastAPI, backend: Backend) -> None:
    store, settings, clock = backend.store, backend.settings, backend.clock
    accounts, providers = backend.accounts, backend.identity_providers

    # One-off codes for the phone's own HTTP client, made when the browser finishes signing
    # in and redeemed once by `exchange_mobile_sign_in`. They live only in this process and
    # for `MOBILE_CODE_LIFETIME`; a restart only means signing in again.
    mobile_codes: dict[str, tuple[str, datetime]] = {}
    mobile_codes_lock = threading.Lock()

    def issue_mobile_code(user_id: str) -> str:
        code = secrets.token_urlsafe(32)
        now = clock()
        expires = now + timedelta(seconds=MOBILE_CODE_LIFETIME)
        with mobile_codes_lock:
            for old, (_, old_expires) in list(mobile_codes.items()):
                if old_expires <= now:
                    mobile_codes.pop(old, None)
            while len(mobile_codes) >= 1024:
                mobile_codes.pop(next(iter(mobile_codes)), None)
            mobile_codes[code] = (user_id, expires)
        return code

    def redeem_mobile_code(code: str) -> str | None:
        with mobile_codes_lock:
            found = mobile_codes.pop(code, None)
        if found is None:
            return None
        user_id, expires = found
        if expires <= clock():
            return None
        return user_id

    def callback_url(provider: str) -> str:
        return f"{settings.public_origin}/api/auth/{provider}/callback"

    async def signed_in(user_id: str, response: Response, request: Request) -> None:
        """Start a session for the user in this browser, ending the one it had."""
        if old := request.cookies.get(SESSION_COOKIE):
            await store.end_session(old)
        set_session_cookie(response, await store.create_session(user_id, clock()), settings)

    @app.get("/api/auth/providers")
    def sign_in_providers() -> dict:
        """The ways to sign in on offer, in the order to show them, and how to reach the
        deployer, which the sign-in page tells a banned user (None if the deployer gave
        none)."""
        return {
            "providers": [p for p in PROVIDER_NAMES if p in providers],
            "dev_login": settings.dev_login,
            "contact": settings.contact,
        }

    @app.get("/api/auth/{provider}/start")
    async def start_sign_in(
        provider: str, request: Request, link: bool = False, mobile: bool = False
    ) -> RedirectResponse:
        """Off to the provider to sign in, or with `link`, to link another identity to the
        signed-in user; with `mobile`, back to the phone's own app instead of to a page."""
        identity_provider = providers.get(provider)  # pyright: ignore[reportArgumentType]
        if identity_provider is None:
            raise HTTPException(404, "没有这种登录方式")
        if link and mobile:
            raise HTTPException(400, "不能同时绑定身份和手机登录")
        if link:
            user = await session_user(request)
            if user is None or user.consented_at is None:
                return RedirectResponse("/login", status_code=302)
        state = f"{'m' if mobile else 'l' if link else 'w'}.{secrets.token_urlsafe(24)}"
        response = RedirectResponse(
            identity_provider.authorize_url(state, callback_url(provider)), status_code=302
        )
        purpose = "mobile" if mobile else "link" if link else "login"
        response.set_cookie(
            STATE_COOKIE,
            f"{purpose}:{provider}:{state}",
            max_age=STATE_LIFETIME,
            path="/api/auth",
            httponly=True,
            samesite="lax",
            secure=settings.domain is not None,
        )
        return response

    @app.get("/api/auth/{provider}/callback")
    async def finish_sign_in(
        provider: str,
        request: Request,
        code: str | None = None,
        state: str | None = None,
        error: str | None = None,
    ) -> Response:
        purpose, expected_provider, expected_state = (
            request.cookies.get(STATE_COOKIE, "").split(":", 2) + ["", ""]
        )[:3]
        # The state's prefix still says the browser came from the phone's app when the
        # state cookie is gone; then the app can be told why instead of a dead web page.
        mobile = purpose == "mobile" or (state or "").startswith("m.")
        linking = purpose == "link"
        back = "/settings/sign-in" if linking else "/login"

        def go(to: str) -> RedirectResponse:
            response = RedirectResponse(to, status_code=302)
            response.delete_cookie(STATE_COOKIE, path="/api/auth")
            return response

        def mobile_page(params: dict[str, str]) -> HTMLResponse:
            """A page that opens the app, with a link in case the browser will not."""
            target = f"{MOBILE_REDIRECT}?{urlencode(params)}"
            response = HTMLResponse(
                "<!doctype html><meta charset=utf-8>"
                "<meta name=viewport content='width=device-width,initial-scale=1'>"
                "<title>登录完成</title>"
                "<body style='font:16px system-ui;padding:2rem'>"
                "<p>登录完成，正在回到 Eidolon…</p>"
                f"<p><a href='{html.escape(target, quote=True)}'>没有自动打开？点这里回到 Eidolon</a></p>"
                f"<script>location.href={json.dumps(target)}</script>"
                "</body>"
            )
            response.delete_cookie(STATE_COOKIE, path="/api/auth")
            return response

        def fail(reason: str) -> Response:
            if mobile:
                return mobile_page({"error": reason})
            return go(f"{back}?error={reason}")

        identity_provider = providers.get(provider)  # pyright: ignore[reportArgumentType]
        if (
            identity_provider is None
            or not state
            or not expected_state
            or expected_provider != provider
            or not secrets.compare_digest(state, expected_state)
        ):
            return fail("state")
        if linking and mobile:
            return fail("state")
        if error or not code:
            return fail("cancelled")
        try:
            identity = await identity_provider.identify(code, callback_url(provider))
        except IdentityError:
            log.warning("signing in with %s failed", provider, exc_info=True)
            return fail("provider")
        try:
            if linking:
                user = await session_user(request)
                if user is None or user.consented_at is None:
                    return go("/login?error=state")
                await accounts.link(user.id, identity)
                return go(f"/settings/sign-in?linked={provider}")
            user = await accounts.sign_in(identity)
        except SignInRefused as e:
            return fail(e.reason)
        if mobile:
            return mobile_page({"code": issue_mobile_code(user.id)})
        response = go("/conversations")
        await signed_in(user.id, response, request)
        return response

    @app.post("/api/auth/mobile/exchange", status_code=204)
    async def exchange_mobile_sign_in(
        body: MobileExchange, request: Request, response: Response
    ) -> None:
        """Take the one-off code from the phone's browser and start the session in its own
        HTTP client, whose cookie jar the app keeps. The code works once and briefly."""
        user_id = redeem_mobile_code(body.code)
        if user_id is None:
            raise HTTPException(401, "登录码已失效，请重新登录")
        try:
            user = await store.get_user(user_id)
        except NotFound:
            raise HTTPException(401, "登录码已失效，请重新登录") from None
        if user.banned_at is not None:
            raise HTTPException(403, "这位用户已被封禁")
        await signed_in(user.id, response, request)

    @app.post("/api/auth/logout", status_code=204)
    async def logout(request: Request, response: Response) -> None:
        """Sign this browser out; its session ends even if it has already expired."""
        if token := request.cookies.get(SESSION_COOKIE):
            await store.end_session(token)
        clear_session_cookie(response, settings)

    @app.get("/api/account")
    async def account(user_id: UserId) -> dict:
        """The signed-in user, by the id their browser keeps their own keys apart by, the
        identities they sign in with, whether they are an admin, who may enter the admin
        console, the exempting keys now (GLOSSARY.md「免限额密钥」), in a fixed order, and
        whether they have their own switch for reach-outs on (GLOSSARY.md「搭话」), None where
        the deployer has reach-outs off, and the version of the sponsor code (GLOSSARY.md
        「赞赏」) while users are shown it, else None."""
        sponsor = await backend.sponsor.state()
        user = await store.get_user(user_id)
        methods = await store.sign_in_methods(user_id)
        reach_outs = await backend.reach_outs.switch_of(user_id)
        return {
            "id": user_id,
            "name": user.name,
            "sign_in_methods": [
                {"provider": m.provider, "display_name": m.display_name, "email": m.email}
                for m in methods
            ],
            "is_admin": signs_in_as_admin(methods, settings),
            "exempting_keys": [s for s in SERVICES if s in backend.config.exempting_keys],
            "reach_outs": reach_outs,
            "sponsor_version": sponsor.version,
        }

    @app.put("/api/account/reach-outs", status_code=204)
    async def switch_reach_outs(body: ReachOutSwitch, user_id: UserId) -> None:
        """Turn the user's own switch for reach-outs on or off, for all the characters; 409
        where the deployer has reach-outs off."""
        try:
            await backend.reach_outs.switch(user_id, body.on)
        except ReachOutsOff:
            raise HTTPException(409, "搭话没有开放") from None

    @app.delete("/api/account/sign-in-methods/{provider}", status_code=204)
    async def unlink(provider: str, user_id: UserId) -> None:
        """Stop signing in with the identity at `provider`; never the last one."""
        try:
            found = await store.remove_sign_in_method(user_id, provider)
        except LastSignInMethod:
            raise HTTPException(409, "这是你唯一的登录方式，解绑后就无法再登录了") from None
        if not found:
            raise HTTPException(404, "没有绑定这种登录方式")

    @app.post("/api/account/logout-everywhere", status_code=204)
    async def logout_everywhere(user_id: UserId, response: Response) -> None:
        """Sign the user out on every device, this one included."""
        await store.end_sessions(user_id)
        clear_session_cookie(response, settings)

    @app.post("/api/account/delete", status_code=204)
    async def delete_account(body: Deletion, user: SignedIn, response: Response) -> None:
        """Delete the user and all their data for good, signing them out everywhere; allowed
        before they agree to the terms too."""
        await accounts.delete(user.id)
        clear_session_cookie(response, settings)

    own_keys = backend.own_keys
    throttle = Throttle(backend.clock)

    @app.exception_handler(OwnKeyRejected)
    async def own_key_rejected(_: Request, e: OwnKeyRejected) -> JSONResponse:
        """A user's own key failed its try: what the service said."""
        return JSONResponse({"detail": f"试调失败：{e}"}, status_code=422)

    @app.get("/api/account/keys/exemption")
    def exemption(user_id: UserId, own: str = "") -> dict:
        """Whether the user's text turns and calls would count towards nothing, if they
        brought their own keys for the services `own` names (as `llm,tts,stt`), but none
        of the keys: only by the rule, whatever their own limits and whether calls are
        available."""
        services = named_services(own)
        return {
            "turns": own_keys.exempt("turn", services),
            "calls": own_keys.exempt("call", services),
        }

    @app.post("/api/account/keys/llm/check")
    async def check_own_llm(body: OwnChatLLMInput, user_id: UserId) -> dict:
        """Try the user's own LLM at its text effort and its call effort before their
        browser keeps it, keeping nothing here; 422 with what the service said if the try
        failed, or with why its effort or extra body cannot be sent. Its address and model
        as the browser is to keep them."""
        own = body.chat_settings()
        with throttle.asking(user_id):
            await own_keys.check_llm(own)
        return {"base_url": own.text.base_url, "model": own.text.model}

    reach_out_models = backend.reach_out_models

    @app.get("/api/account/reach-out-llm")
    def reach_out_llm(user_id: UserId) -> dict:
        """The model kept for the user's reach-outs, without its key: its address, model,
        protocol and the key's last 4 characters; `configured` false if none."""
        described = reach_out_models.describe(user_id)
        return {"configured": described is not None, **(described or {})}

    @app.put("/api/account/reach-out-llm", status_code=204)
    async def set_reach_out_llm(body: OwnLLMInput, user_id: UserId) -> None:
        """Keep a model for the user's reach-outs, encrypted with the deployment's secret
        key, after trying it as their own LLM is tried; 503 where the deployment has no
        `EIDOLON_SECRET_KEY`."""
        if not reach_out_models.configured:
            raise HTTPException(503, "服务器没有设置 EIDOLON_SECRET_KEY，不能保存搭话模型")
        own = body.settings()
        with throttle.asking(user_id):
            await own_keys.check_llm(ChatModelSettings(own))
        reach_out_models.save(user_id, own)

    @app.delete("/api/account/reach-out-llm", status_code=204)
    def delete_reach_out_llm(user_id: UserId) -> None:
        """Forget the user's reach-out model."""
        reach_out_models.delete(user_id)

    @app.post("/api/account/keys/memory-llm/check")
    async def check_own_memory_llm(body: OwnLLMInput, user_id: UserId) -> dict:
        """Try the user's own model for memories at its effort before their browser keeps
        it, as `check_own_llm` does their LLM; why it failed comes after saying it was this
        one."""
        own = body.settings()
        with throttle.asking(user_id):
            await own_keys.check_memory_llm(own)
        return {"base_url": own.base_url, "model": own.model}

    @app.get("/api/account/keys/llm/efforts")
    async def llm_efforts(
        user_id: UserId,
        protocol: LLMProtocol,
        base_url: Annotated[str, Query(max_length=500)],
        model: Annotated[str, Query(max_length=200)],
    ) -> dict | None:
        """How the model at `base_url` named `model` thinks, as models.dev lists it, for
        the efforts `protocol` sends (`price_catalog.describe_thinking`), or None if it
        is not listed, which has the catalogs fetched again if they were last tried a
        while ago. A hint for the deployer's model and a user's own alike: no key needed,
        nothing kept."""
        catalog = backend.prices.catalog
        try:  # the host, as the model's calls are priced by
            thinking = catalog.thinking(host(base_url.strip()), model.strip())
        except ValueError:  # not an address (`http://[`)
            thinking = None
        if thinking is None:
            catalog.refresh_soon(missing=True)
            return None
        return describe_thinking(thinking, protocol)

    @app.post("/api/account/keys/llm/models")
    async def list_own_llm_models(body: OwnLLMListInput, user_id: UserId) -> dict:
        """The models the user's key is offered at the address, as the service lists them
        (`listing.list_models`), for the settings page to pick one from; keeping nothing
        here, and asked as often as a key is tried (`throttle`). 422 with what the service
        said if it did not list them."""
        with throttle.asking(user_id):
            try:
                models = await own_keys.list_llm_models(
                    body.protocol, body.base_url, body.api_key.get_secret_value()
                )
            except OwnKeyRejected as e:
                raise HTTPException(422, f"获取模型失败：{e}") from None
        return {"models": models}

    @app.get("/api/account/keys/llm/providers")
    async def llm_providers(user_id: UserId, response: Response) -> dict:
        """The providers an LLM may be picked from, as models.dev lists them
        (`price_catalog.parse_offers`): where each serves its models and over which
        protocol, for the settings page and the admin console to fill in, and every address
        its models are at, for them to tell which an LLM saved is at. None (an empty list)
        before the catalogs are first fetched, which has them fetched if they were last
        tried a while ago. No key needed, nothing kept."""
        catalog = backend.prices.catalog
        providers = catalog.providers()
        if not providers:
            catalog.refresh_soon(missing=True)
        else:  # the same for everyone, and fetched again once a day
            response.headers["Cache-Control"] = "private, max-age=3600"
        return {"providers": [describe_provider(p, catalog.offers(p.id)) for p in providers]}

    @app.get("/api/account/keys/llm/providers/{provider}/models")
    def llm_provider_models(
        user_id: UserId, provider: Annotated[str, Path(max_length=200)], response: Response
    ) -> dict:
        """The models `provider` serves that an LLM may be picked from, each with where and
        over which protocol (a provider may serve some over another); none for a provider
        not listed."""
        offers = backend.prices.catalog.offers(provider)
        if offers:
            response.headers["Cache-Control"] = "private, max-age=3600"
        return {"models": [describe_offer(o) for o in offers]}

    @app.post("/api/account/keys/tts/check")
    async def check_own_tts(body: OwnTTSInput, user_id: UserId) -> dict:
        """Try the user's own key for speech synthesis before their browser keeps it,
        keeping nothing here; 422 with what the service said if the try failed."""
        with throttle.asking(user_id):
            await own_keys.check_tts(body.api_key.get_secret_value(), body.model)
        return {}

    @app.post("/api/account/keys/stt/check")
    async def check_own_stt(body: OwnSTTInput, user_id: UserId) -> dict:
        """Try the user's own key for speech recognition, by the service it is for, before
        their browser keeps it, keeping nothing here; 422 with what the service said if
        the try failed. A DashScope key is tried in each region: the one it works in, for
        the browser to keep with it (None for Soniox)."""
        with throttle.asking(user_id):
            key = await own_keys.check_stt(body.provider, body.api_key.get_secret_value())
        return {"region": key.region}

    @app.post("/api/account/keys/image/check")
    async def check_own_image(body: OwnImageInput, user_id: UserId) -> dict:
        """Try the user's own image generation before their browser keeps it, by generating
        one picture at their expense in the deployment's art style, keeping, counting and
        recording nothing; 422 with what the service said if the try failed, or untried
        where there is no judge model, without which no turn has a photo. Its address and
        model as the browser is to keep them."""
        if backend.config.judge_settings is None:
            raise OwnKeyRejected(NO_PHOTOS)
        own = body.settings()
        with throttle.asking(user_id):
            await own_keys.check_image(own)
        return {"base_url": own.base_url, "model": own.model}

    @app.post("/api/account/consent", status_code=204)
    async def consent(body: Consent, user: SignedIn) -> None:
        """Agree to the privacy policy and terms of use, which the rest of the API waits for."""
        await store.consent(user.id, clock())

    if settings.dev_login:

        @app.post("/api/auth/dev-login", status_code=204)
        async def dev_login(body: DevLogin, request: Request, response: Response) -> None:
            """Sign in as the user with this name, made if there is none (development only)."""
            user = await store.first_user_named(body.name) or await store.create_user(body.name)
            await signed_in(user.id, response, request)
