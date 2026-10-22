"use client";

import { FormEvent, useEffect, useState } from "react";

import { FailureNotice } from "@/components/app/FailureNotice";
import { MarkedAvatar } from "@/components/app/MarkedAvatar";
import { PageShell } from "@/components/app/PageShell";
import { SignInChoices } from "@/components/app/SignInChoices";
import { SectionHeading } from "@/components/app/SubpageHeader";
import { LegalFooter } from "@/components/LegalFooter";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { copy } from "@/i18n";
import {
  devLogin,
  getSignInOptions,
  listCharacters,
  type Character,
  type SignInOptions,
} from "@/lib/api";
import { APP_DOWNLOAD_URL } from "@/lib/appDownload";
import { toFailure, type Failure } from "@/lib/failure";
import { signInFailure } from "@/lib/signIn";
import { FailureIcon } from "@/theme/icons";

const t = copy.login;

/** 登录页：主入口是下载 Android App。登录被拒时后端带着 `?error=` 回到这里；网页登录收在折叠区，留给管理后台和异常处理。 */
export default function LoginPage() {
  const [options, setOptions] = useState<SignInOptions | null>(null);
  const [characters, setCharacters] = useState<Character[]>([]);
  const [refusal, setRefusal] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [failure, setFailure] = useState<Failure | null>(null);

  useEffect(() => {
    setRefusal(new URLSearchParams(window.location.search).get("error"));
    getSignInOptions().then(setOptions, (e) => setFailure(toFailure(e)));
    // 角色名单随 characters.toml 变化，从后端取；取不到只是少了展示，不算失败。
    listCharacters().then(setCharacters, () => {});
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFailure(null);
    try {
      await devLogin(name.trim());
      window.location.assign("/conversations");
    } catch (e) {
      setFailure(toFailure(e));
    }
  }

  const refusalText = refusal ? signInFailure(refusal, options?.contact) : null;

  return (
    <PageShell footer={<LegalFooter />}>
      <div className="flex flex-col gap-8 py-2">
        <div className="flex flex-col gap-3">
          <h1 className="text-list-title">{copy.app.name}</h1>
          <p className="text-body">{t.lede}</p>
        </div>

        {/* 登录被拒绝：和 FailureNotice 一样用中性的 Alert 加警示图标，挂断红只给挂断和删除、封禁（DESIGN.md「Tertiary」）。 */}
        {refusal && (
          <Alert>
            <FailureIcon />
            <AlertTitle>{t.refusedTitle}</AlertTitle>
            {refusalText && <AlertDescription>{refusalText}</AlertDescription>}
          </Alert>
        )}

        {/* 网页版的主入口改成下载 App；网页登录收进下面，留给管理后台和登录异常处理。 */}
        <section className="flex max-w-90 flex-col gap-3">
          <h2 className="text-list-title">{t.downloadTitle}</h2>
          <p className="text-body text-muted-foreground">{t.downloadHint}</p>
          <a href={APP_DOWNLOAD_URL} download className={buttonVariants({ size: "lg" })}>
            {t.download}
          </a>
        </section>

        <details className="max-w-90">
          <summary className="cursor-pointer text-body text-muted-foreground">{t.webSignIn}</summary>
          <div className="mt-4 flex flex-col gap-4">
            <SignInChoices options={options} />

            {options?.dev_login && (
              <form onSubmit={submit} className="flex flex-col gap-3">
                <div className="flex flex-col gap-1">
                  <SectionHeading>{t.devTitle}</SectionHeading>
                  <p className="text-body text-muted-foreground">{t.devHint}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Input aria-label={t.devName} value={name} onChange={(e) => setName(e.target.value)} className="flex-1" />
                  <Button type="submit" disabled={!name.trim()}>
                    {t.devSubmit}
                  </Button>
                </div>
              </form>
            )}

            {failure && <FailureNotice failure={failure} />}
          </div>
        </details>

        {characters.length > 0 && (
          <section className="flex flex-col gap-2">
            <SectionHeading>{t.castTitle}</SectionHeading>
            {/* 偶像名单是行，不套卡片（DESIGN.md「Conversation list」）。 */}
            <ul className="flex flex-col">
              {characters.map((character) => (
                <li key={character.id} className="flex items-center gap-3 py-3">
                  <MarkedAvatar name={character.name} src={character.avatar_url} />
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <span className="text-row-name">{character.name}</span>
                    <span className="text-sm text-muted-foreground">{character.intro}</span>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </PageShell>
  );
}
