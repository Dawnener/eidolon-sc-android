# Android 壳

把局域网上的 Eidolon-SC 网页装进一个全屏 WebView，供手机调试用。它不是第二套前端：页面、路由、样式都来自 Next.js，这里只负责加载地址、麦克风授权、文件选择、下载、返回键和外部链接。

当前范围是**先能看到界面**：落地页、开发登录、文字对话。语音通话需要 HTTPS（麦克风只在安全上下文里可用），暂未配置；APK 里已经把麦克风权限流程写好，以后加 HTTPS 或 `adb reverse` 到 localhost 就能用。

## 原生客户端（`native` 模块）

新的原生界面在 `android/native/`（Kotlin + Jetpack Compose），和上面的 WebView 壳并存，两个 APK 可以同时安装：

- 原生 App 直接调后端的 `/api/*`，不加载网页；需要单独构建：

```bash
source ~/.config/eidolon-android/env.sh
cd android
./gradlew :native:assembleDebug
# 产物：android/native/build/outputs/apk/debug/native-debug.apk
```

- 默认服务器地址是 `http://192.168.114.40:8080`，首次启动可以改；启动栈后从 `http://<局域网 IP>:8080/apk/` 下载原生包（首页有 Release 和 Debug 两个链接）。
- 第一版范围：开发登录、同意条款、对话列表、聊天（流式日语 + 中文字幕）、设置、自带密钥（LLM / 后台 LLM / TTS / STT / 生图）、记忆。
- 暂不包含：语音通话/WebRTC、礼物、好感度、统计、成就、后台、落地页 3D、Spine 立绘、历史对话浏览、照片轮询。
- 自带密钥保存在设备的 Android Keystore 加密存储里，按「部署 + 用户」隔离：后端在 `/api/health` 返回一个存在数据库里的 `deployment_id`，同一个后端换 IP 或域名仍然读得到密钥，切换到另一个部署则不会串。旧版本按服务器地址存下的密钥，在仍连在同一地址时会自动迁移到部署命名空间。退出登录会清除当前用户在当前部署下的密钥。
- 单元测试：`./gradlew :native:testDebugUnitTest`。

网页壳 `:app` 仍然可以用 `./gradlew :app:assembleDebug` 构建，产物是 `android/app/build/outputs/apk/debug/app-debug.apk`；它适合临时看网页版。

## 前置条件

- 这台 WSL2 机器和手机在**同一个 Wi-Fi**，且网络没有开启客户端隔离（酒店、访客网络常会隔离）。
- 手机 Android 8+，建议把「Android System WebView」更新到最新。
- 仓库根目录有 `.env`，至少填好：

  ```dotenv
  CHAT_LLM_PROTOCOL=          # OpenAI 兼容留空；Anthropic 填 anthropic；Responses 填 responses
  CHAT_LLM_BASE_URL=
  CHAT_LLM_API_KEY=
  CHAT_LLM_MODEL=
  EIDOLON_DEV_LOGIN=1
  EIDOLON_DOMAIN=
  ```

  `CHAT_LLM_*` 三项可以全部留空，走**纯自带密钥模式**：在 App 的「设置 → 自带密钥」里填自己的 LLM。留空时后端不会在启动时检查模型；填了的话三项要一起填，并且模型要支持流式输出和工具调用。

## 一次性准备

安装 JDK 17、Android SDK 和 Gradle 到 `~/.local/opt/eidolon-android`（不需要 sudo，大约 1 GB）：

```bash
bash scripts/android-toolchain.sh
source ~/.config/eidolon-android/env.sh
```

## 启动本地栈

```bash
bash scripts/dev-local.sh start     # 后端 :8000、前端 :3000、Caddy :8080
bash scripts/dev-local.sh status
bash scripts/dev-local.sh stop
```

第一次会安装 uv、下载 Caddy、安装前端依赖并构建 Next（几分钟）。之后 `start` 只起进程；改了前端代码用 `bash scripts/dev-local.sh rebuild`。

脚本会打印两个地址，例如：

```text
手机打开：http://192.168.114.40:8080/
APK 下载：http://192.168.114.40:8080/apk/
```

局域网 IP 默认按 `ip route get 1.1.1.1` 自动取；要固定或改掉，用 `EIDOLON_LAN_IP=192.168.1.20 bash scripts/dev-local.sh start`。

## 构建 APK

```bash
source ~/.config/eidolon-android/env.sh
cd android
./gradlew assembleDebug
```

产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。第一次构建会下载 Android Gradle Plugin，需要几分钟；调试包用自动生成的 debug keystore 签名，可直接侧载。

脚本里的默认服务器地址写在 `android/app/src/main/res/values/strings.xml` 的 `default_server_url`。换了网络导致 IP 变化时不用重新打包：在应用里改地址即可。

## 装到手机

1. 手机浏览器打开 `http://<局域网 IP>:8080/`，确认落地页能显示。打不开先看下面的防火墙。
2. 打开 `http://<局域网 IP>:8080/apk/`，点页面里的下载链接（直接打开 `/apk/app-debug.apk` 也行，注意文件名是 `app-debug.apk`，不是 `apk-debug.apk`），按提示允许浏览器「安装未知应用」，然后安装。
3. 启动 **Eidolon**。首次会显示地址设置，确认后点「打开」。
4. 落地页 → 登录 → 开发登录 → 输入名字 → 同意隐私政策和使用条款 → 对话列表。

应用启动时会自动加载上次的地址；主页面加载失败时会把地址浮层叫出来，改完点「打开」重试。换地址会清掉旧服务器的 cookie、缓存和 Web Storage。

## 返回键

网页的导航历史和应用的原生返回不是一回事：应用里从主界面进设置、再回主界面，历史栈里还留着设置。壳按页面层级处理返回：

- 顶层页面（`/`、`/conversations`、`/settings`、`/gifts`、`/admin`）按返回**退出应用**。
- 子页面按返回先回上一级：`/conversation/*` → 对话列表，`/settings/*` → 设置首页，`/admin/*` → 后台首页；`/login`、`/consent`、`/about`、`/privacy`、`/terms` 有历史就回历史，没有就回 `/`。有可回退的网页历史就用它（保留页面状态），没有就直接打开上级地址。
- 以后开通话时要注意：在通话中的对话页按返回会离开这一页，不会经过网页里的挂断确认；到时需要再加一层处理。

## 防火墙

WSL2 是 mirrored 网络，Windows 的 WLAN IP 就是 WSL 的 IP，但 Windows 防火墙仍会拦入站。当前 Wi-Fi 配置为 **Public**，用管理员 PowerShell 放行 8080：

```powershell
New-NetFirewallRule -DisplayName "Eidolon dev 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Public
```

用完可以删掉：

```powershell
Remove-NetFirewallRule -DisplayName "Eidolon dev 8080"
```

如果不能提权，退路是 `adb reverse tcp:3000 tcp:3000`，把应用里的地址改成 `http://localhost:3000`（需要手机开启无线调试并保持 adb 连接）。

## 登录与 Origin

后端在开发模式下只接受 `http://localhost`、`127.0.0.1`、`::1` 的 `Origin`。手机发来的是 `http://<局域网 IP>:8080`，所以 `scripts/dev/Caddyfile` 把上游 `Origin` 改写成 `http://localhost:3000`，同时保留 `Host`（Next 用它生成跳转地址）。这是本地开发专用配置，公开部署不要照搬。

## 排错

| 现象 | 先看 |
| --- | --- |
| 手机打不开地址 | Windows 防火墙是否放行 8080；手机和电脑是否同一 Wi-Fi；路由器是否隔离客户端 |
| 页面能开，登录返回 403 | Caddy 是否在跑；请求是否绕过了 8080 直连 3000 |
| 后端起不来 | `logs/local-stack/backend.log`；多数是 `CHAT_LLM_*` 只填了一部分，或模型不支持流式/工具调用。三项全空是纯自带密钥模式，后端会正常启动 |
| 前端构建失败 | `logs/local-stack/frontend.log`；`next/font/google` 取字体失败时可试 `NODE_OPTIONS=--use-env-proxy` |
| 页面布局错乱/白屏 | 更新手机的 Android System WebView；用桌面 Chrome 的 `chrome://inspect` 连调试包排查 |
| 应用里想换地址 | 先让当前地址加载失败（显示浮层），或清除应用数据；地址会回到默认值 |

日志和 PID 都在 `logs/local-stack/`（已被 git 忽略）。

## 安全提醒与已知限制

- 开发登录在局域网内对所有人开放：任何能访问 8080 的人都能冒充任意名字。只在可信网络使用，用完 `bash scripts/dev-local.sh stop`。
- 没有 HTTPS，所以**不能通话**；麦克风需要安全上下文。以后要做通话，可以给 Caddy 配自建 CA 的 HTTPS（并把根证书打进 APK 或装进手机），或者用 `adb reverse` 让页面从 `http://localhost` 加载。
- 分享图导出用的是 `blob:` 下载，WebView 的下载监听接不住；数据包导入/导出走普通 http(s) 下载，正常。
- 没有 `corpus/` 时剧情回忆不可用；不影响界面和文字对话。
- Caddy 改写 `Origin` 是本地开发绕过；如果以后经常在局域网上调试，更干净的做法是给后端加一个 `EIDOLON_DEV_ORIGINS` 允许列表并补测试。
