#!/usr/bin/env bash
# 本地开发栈：后端 :8000、Next standalone :3000、Caddy :8080（手机访问的入口）。
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
APK_DIR="$ROOT/android/app/build/outputs/apk/debug"
NATIVE_APK_DIR="$ROOT/android/native/build/outputs/apk"

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
  [ -f "$ROOT/.env" ] || die "没有 $ROOT/.env：先 cp .env.example .env，再填 CHAT_LLM_* 和 EIDOLON_DEV_LOGIN=1"
  # CHAT_LLM_* 三项可以都留空（纯自带密钥模式）；只填一部分时后端会拒绝启动并说明
  [ "$(env_value EIDOLON_DEV_LOGIN)" = "1" ] || die ".env 里要设 EIDOLON_DEV_LOGIN=1"
  [ -z "$(env_value EIDOLON_DOMAIN)" ] || die ".env 里的 EIDOLON_DOMAIN 要留空；开发登录和它不能同时用"
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

build_frontend() {
  local force="${1:-}"
  if [ "$force" != "force" ] && [ -f "$ROOT/frontend/.next/standalone/server.js" ]; then
    return
  fi
  (
    cd "$ROOT/frontend"
    if [ ! -d node_modules ]; then
      echo "安装前端依赖"
      PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm ci
    fi
    echo "构建前端"
    BACKEND_URL=http://127.0.0.1:8000 npm run build
    rm -rf .next/standalone/public .next/standalone/.next/static
    mkdir -p .next/standalone/.next
    cp -r public .next/standalone/public
    cp -r .next/static .next/standalone/.next/static
  )
}

start_backend() {
  if running backend; then
    echo "后端已在运行（pid $(cat "$(pid_file backend)")）"
    return
  fi
  echo "起后端 :8000"
  (
    cd "$ROOT/backend"
    start_bg backend env HOST=127.0.0.1 PORT=8000 uv run --quiet --env-file=../.env eidolon serve
  )
  if ! wait_http http://127.0.0.1:8000/api/health 120; then
    echo "后端没起来，最后几行日志：" >&2
    tail -n 40 "$STATE/backend.log" >&2 || true
    exit 1
  fi
}

start_frontend() {
  if running frontend; then
    echo "前端已在运行（pid $(cat "$(pid_file frontend)")）"
    return
  fi
  echo "起前端 :3000"
  (
    cd "$ROOT/frontend"
    start_bg frontend env HOSTNAME=127.0.0.1 PORT=3000 NODE_ENV=production \
      BACKEND_URL=http://127.0.0.1:8000 node .next/standalone/server.js
  )
  wait_http http://127.0.0.1:3000/ 60 || die "前端没起来，看 $STATE/frontend.log"
}

start_caddy() {
  if running caddy; then
    echo "Caddy 已在运行（pid $(cat "$(pid_file caddy)")）"
    return
  fi
  echo "起 Caddy :8080"
  start_bg caddy env EIDOLON_APK_DIR="$APK_DIR" EIDOLON_NATIVE_APK_DIR="$NATIVE_APK_DIR" "$CADDY" run \
    --config "$ROOT/scripts/dev/Caddyfile" --adapter caddyfile
  wait_http http://127.0.0.1:8080/ 30 || die "Caddy 没起来，看 $STATE/caddy.log"
}

start() {
  ensure_env
  ensure_uv
  ensure_caddy
  build_frontend "${1:-}"
  start_backend
  start_frontend
  start_caddy
  echo
  echo "手机打开：http://$LAN_IP:8080/"
  if [ -f "$APK_DIR/app-debug.apk" ] || [ -f "$NATIVE_APK_DIR/native-debug.apk" ]; then
    echo "APK 下载：http://$LAN_IP:8080/apk/（原生 App / 网页壳）"
  else
    echo "还没构建 APK：先 bash scripts/android-toolchain.sh，再 cd android && ./gradlew assembleDebug"
  fi
  echo "日志：$STATE/"
}

stop() {
  local name pid
  for name in caddy frontend backend; do
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
  for name in backend frontend caddy; do
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
