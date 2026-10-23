#!/usr/bin/env bash
# 本地开发栈：后端 :8000、Caddy :8080（手机访问的入口，提供 API、下载页和立绘）。
# 如果本机配了 ~/.cloudflared/config.yml，还会启动 Cloudflare Tunnel；
# 公网身份校验由 Cloudflare Access 负责。
#
# 用法：
#   bash scripts/dev-local.sh start     # 起后端、前端和 Caddy（默认）
#   bash scripts/dev-local.sh rebuild   # 重新构建前端再起
#   bash scripts/dev-local.sh stop      # 全停
#   bash scripts/dev-local.sh status    # 看谁在跑、健康检查
#
# .env 在仓库根目录，要有 EIDOLON_DEV_LOGIN=1。CHAT_LLM_* 三项可以填齐（部署者模式），
# 也可以全部留空（纯自带密钥模式，用户在原生 App 的「自带密钥」里填自己的 LLM）。
# 日志和 PID 在 logs/local-stack/。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE="$ROOT/logs/local-stack"
CADDY="$HOME/.local/opt/caddy/caddy"
NATIVE_APK_DIR="$ROOT/android/native/build/outputs/apk"
# 可选：本机已经建好 Cloudflare Tunnel 时，顺手启动公网入口。
# 配置放在仓库外，里面是本机凭证，不提交。
CLOUDFLARED_CONFIG="${EIDOLON_CLOUDFLARED_CONFIG:-$HOME/.cloudflared/config.yml}"
CLOUDFLARED="${CLOUDFLARED:-$(command -v cloudflared || true)}"
PUBLIC_HOST="$(awk '/^[[:space:]]*- hostname:/{print $3; exit}' "$CLOUDFLARED_CONFIG" 2>/dev/null || true)"

LAN_IP="${EIDOLON_LAN_IP:-}"
if [ -z "$LAN_IP" ]; then
  LAN_IP="$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}')"
fi
LAN_IP="${LAN_IP:-127.0.0.1}"

die() {
  echo "$*" >&2
  exit 1
}

env_value() {
  grep -E "^$1=" "$ROOT/.env" | tail -n 1 | cut -d= -f2-
}

ensure_env() {
  [ -f "$ROOT/.env" ] || die "没有 $ROOT/.env：先 cp .env.example .env，再填所需配置"
  # 两种模式：本地开发登录（EIDOLON_DEV_LOGIN=1）或公开部署（EIDOLON_DOMAIN），不能同时开。
  if [ "$(env_value EIDOLON_DEV_LOGIN)" = "1" ] && [ -n "$(env_value EIDOLON_DOMAIN)" ]; then
    die ".env 里 EIDOLON_DEV_LOGIN 和 EIDOLON_DOMAIN 不能同时设置"
  fi
}

ensure_uv() {
  # 新 shell 的 PATH 里通常没有 ~/.local/bin，装过就直接用，别重复装
  if [ -x "$HOME/.local/bin/uv" ]; then
    export PATH="$HOME/.local/bin:$PATH"
    return
  fi
  command -v uv >/dev/null 2>&1 && return
  echo "安装 uv 到 ~/.local/bin"
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$PATH"
  command -v uv >/dev/null 2>&1 || die "uv 安装后仍找不到，试试新开一个 shell"
}

ensure_caddy() {
  [ -x "$CADDY" ] && return
  echo "下载 Caddy 到 $CADDY"
  mkdir -p "$(dirname "$CADDY")"
  curl -fL --retry 3 --retry-delay 2 -o "$CADDY" \
    "https://caddyserver.com/api/download?os=linux&arch=amd64"
  chmod +x "$CADDY"
}

pid_file() {
  printf '%s/%s.pid' "$STATE" "$1"
}

running() {
  local file
  file="$(pid_file "$1")"
  [ -f "$file" ] || return 1
  kill -0 "$(cat "$file")" 2>/dev/null
}

start_bg() {
  local name="$1"
  shift
  mkdir -p "$STATE"
  setsid "$@" >>"$STATE/$name.log" 2>&1 &
  echo $! >"$(pid_file "$name")"
}

wait_http() {
  local url="$1" seconds="$2" i=0
  while [ "$i" -lt "$seconds" ]; do
    if curl -fsS --noproxy '*' -o /dev/null --max-time 2 "$url" 2>/dev/null; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

start_backend() {
  if running backend; then
    echo "后端已在运行（pid $(cat "$(pid_file backend)")）"
    return
  fi
  echo "起后端 :8000"
  (
    cd "$ROOT/backend"
    start_bg backend env HOST=127.0.0.1 PORT=8000 \
      EIDOLON_APK_DIR="$ROOT/android/native/build/outputs/apk/release" \
      uv run --quiet --env-file=../.env eidolon serve
  )
  if ! wait_http http://127.0.0.1:8000/api/health 120; then
    echo "后端没起来，最后几行日志：" >&2
    tail -n 40 "$STATE/backend.log" >&2 || true
    exit 1
  fi
}

start_caddy() {
  if running caddy; then
    echo "Caddy 已在运行（pid $(cat "$(pid_file caddy)")）"
    return
  fi
  echo "起 Caddy :8080"
  start_bg caddy env EIDOLON_NATIVE_APK_DIR="$NATIVE_APK_DIR" \
    EIDOLON_WEB_DIR="$ROOT/deploy/web" \
    EIDOLON_STAND_ASSETS_DIR="$ROOT/corpus/stand/source/assets" \
    EIDOLON_STAND_LISTS_DIR="$ROOT/corpus/stand/outfits" \
    EIDOLON_STAND_THUMBS_DIR="$ROOT/corpus/stand/thumbnails" \
    EIDOLON_STAND_GREETINGS_DIR="$ROOT/corpus/stand/greetings" \
    EIDOLON_STAND_CALL_OPENERS_DIR="$ROOT/corpus/stand/call-openers" "$CADDY" run \
    --config "$ROOT/scripts/dev/Caddyfile" --adapter caddyfile
  wait_http http://127.0.0.1:8080/ 30 || die "Caddy 没起来，看 $STATE/caddy.log"
}

start_tunnel() {
  if [ ! -f "$CLOUDFLARED_CONFIG" ] || [ -z "$CLOUDFLARED" ]; then
    echo "没配置 Cloudflare Tunnel，跳过公网入口"
    return
  fi
  if running cloudflared; then
    echo "Cloudflare Tunnel 已在运行（pid $(cat "$(pid_file cloudflared)")）"
  else
    echo "起 Cloudflare Tunnel"
    start_bg cloudflared "$CLOUDFLARED" tunnel --config "$CLOUDFLARED_CONFIG" run
  fi
}

start() {
  ensure_env
  ensure_uv
  ensure_caddy
  start_backend
  start_caddy
  start_tunnel
  echo
  echo "手机打开：http://$LAN_IP:8080/"
  if [ -n "$PUBLIC_HOST" ]; then
    echo "公网入口：https://$PUBLIC_HOST/（Cloudflare Access）"
  fi
  if ls "$NATIVE_APK_DIR"/debug/*.apk >/dev/null 2>&1; then
    echo "APK 下载：http://$LAN_IP:8080/apk/（Eidolon-SC）"
  else
    echo "还没构建 APK：先 bash scripts/android-toolchain.sh，再 cd android && ./gradlew :native:assembleDebug"
  fi
  echo "日志：$STATE/"
}

stop() {
  local name pid
  for name in cloudflared caddy backend; do
    local file
    file="$(pid_file "$name")"
    [ -f "$file" ] || continue
    pid="$(cat "$file")"
    # start_bg 用 setsid 起了独立进程组，整组一起停，uv 派生的 python 也不会留下
    if kill -0 "$pid" 2>/dev/null; then
      echo "停 $name（pid $pid）"
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
    rm -f "$file"
  done
}

status() {
  local name
  for name in backend caddy cloudflared; do
    if running "$name"; then
      echo "$name: 运行中（pid $(cat "$(pid_file "$name")")）"
    else
      echo "$name: 没在运行"
    fi
  done
  echo -n "后端 /api/health："
  curl -fsS --noproxy '*' --max-time 2 http://127.0.0.1:8000/api/health 2>/dev/null || echo "不可用"
  echo
}

case "${1:-start}" in
  start) start ;;
  rebuild) start force ;;
  stop) stop ;;
  status) status ;;
  *)
    echo "用法：bash scripts/dev-local.sh [start|rebuild|stop|status]" >&2
    exit 2
    ;;
esac
