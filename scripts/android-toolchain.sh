#!/usr/bin/env bash
# 把 Android 构建工具装进用户目录，不需要 sudo。
#
# 装的是：Temurin JDK 17、Android cmdline-tools（含 sdkmanager）、platform-tools、
# platforms;android-35、build-tools;35.0.0、Gradle 8.9。全部放在
# ~/.local/opt/eidolon-android（可以用 EIDOLON_ANDROID_PREFIX 改）。
#
# 用法：
#   bash scripts/android-toolchain.sh
#   source ~/.config/eidolon-android/env.sh
#
# 重复运行是安全的：已经存在的组件会跳过。第一次会下载约 1 GB。

set -euo pipefail

PREFIX="${EIDOLON_ANDROID_PREFIX:-$HOME/.local/opt/eidolon-android}"
SDK="$PREFIX/sdk"
JDK="$PREFIX/jdk-17"
GRADLE="$PREFIX/gradle-8.9"
ENV_FILE="$HOME/.config/eidolon-android/env.sh"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

JAVA_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-8.9-bin.zip"

if [ "$(uname -m)" != "x86_64" ]; then
  echo "只准备了 x86_64 的 Linux 工具链，当前是 $(uname -m)" >&2
  exit 1
fi

mkdir -p "$PREFIX" "$SDK/cmdline-tools"

fetch() {
  local url="$1" out="$2"
  echo "下载 $url"
  curl -fL --retry 3 --retry-delay 2 -o "$out" "$url"
}

extract_zip() {
  # python3 的 zipfile 不会还原可执行位，调用方要补 chmod
  python3 -m zipfile -e "$1" "$2"
}

if [ ! -x "$JDK/bin/java" ]; then
  tmp="$(mktemp -d)"
  fetch "$JAVA_URL" "$tmp/jdk.tar.gz"
  echo "解压 JDK"
  mkdir -p "$JDK"
  tar -xzf "$tmp/jdk.tar.gz" --strip-components=1 -C "$JDK"
  rm -rf "$tmp"
else
  echo "JDK 已存在，跳过"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  tmp="$(mktemp -d)"
  fetch "$CMDLINE_URL" "$tmp/cmdline-tools.zip"
  echo "解压 Android cmdline-tools"
  extract_zip "$tmp/cmdline-tools.zip" "$tmp/unpacked"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/unpacked/cmdline-tools" "$SDK/cmdline-tools/latest"
  chmod +x "$SDK"/cmdline-tools/latest/bin/*
  rm -rf "$tmp"
else
  echo "Android cmdline-tools 已存在，跳过"
fi

if [ ! -x "$GRADLE/bin/gradle" ]; then
  tmp="$(mktemp -d)"
  fetch "$GRADLE_URL" "$tmp/gradle.zip"
  echo "解压 Gradle"
  mkdir -p "$GRADLE"
  extract_zip "$tmp/gradle.zip" "$tmp/unpacked"
  cp -a "$tmp/unpacked/gradle-8.9/." "$GRADLE/"
  chmod +x "$GRADLE/bin/gradle"
  rm -rf "$tmp"
else
  echo "Gradle 已存在，跳过"
fi

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JDK/bin:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$GRADLE/bin:$PATH"

# 接受 SDK 许可证；yes 在 sdkmanager 读完之前退出会得到 SIGPIPE，整条管道用一个 || true 兜住
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"

mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JDK/bin:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$GRADLE/bin:\$PATH"
EOF

if [ -d "$REPO_ROOT/android" ]; then
  printf 'sdk.dir=%s\n' "$SDK" > "$REPO_ROOT/android/local.properties"
  if [ -f "$REPO_ROOT/android/settings.gradle" ] && [ ! -x "$REPO_ROOT/android/gradlew" ]; then
    echo "生成 Gradle wrapper"
    (cd "$REPO_ROOT/android" && "$GRADLE/bin/gradle" wrapper --gradle-version 8.9 --distribution-type bin)
  fi
fi

echo
echo "完成。之后每个新 shell 先执行："
echo "  source $ENV_FILE"
echo "然后构建 APK："
echo "  cd $REPO_ROOT/android && ./gradlew assembleDebug"
