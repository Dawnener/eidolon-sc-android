# Android 客户端

`android/` 里有两套客户端，可以同时安装：

- **`native`（推荐）**：Kotlin + Jetpack Compose，直接调用后端的 `/api/*`，不加载网页。当前覆盖 GitHub 登录、同意条款、对话列表、聊天、制作人档案、历史对话、自带密钥、记忆、搭话、推送、统计/分享图、礼物、好感度、成就和每日任务。
- **`app`（网页壳）**：全屏 WebView，加载 Next.js 网页版。网页版的语音通话、照片、公告、后台等功能都在，适合临时用手机看完整网页版。

两套 APK 的包名不同，同一台手机可以同时装。

## 原生客户端（`native`）

### 构建

```bash
source ~/.config/eidolon-android/env.sh
cd android
./gradlew :native:assembleDebug
# 产物：android/native/build/outputs/apk/debug/native-debug.apk

./gradlew :native:assembleRelease
# 产物：android/native/build/outputs/apk/release/native-release.apk
```

Release 目前用自动生成的 debug keystore 签名，方便侧载和覆盖安装；它不是正式发布签名。

### 功能范围

- 首次安装会看到深色欢迎页，点「使用 GitHub 登录」直接打开 GitHub 授权；服务器地址默认是内置的公开部署，不需要填写；同意条款后进入主界面；
- GitHub 登录、同意条款；其他使用开发登录的服务器仍可在登录页使用开发用户名；
- 对话列表（按角色搜索）、流式日语回复 + 中文字幕、开新对话；
- 设置：服务器、制作人档案（称呼/性别/时区/生日）、自带密钥（LLM / 后台 LLM / TTS / STT / 生图）、搭话开关、搭话模型、记忆、统计、退出登录、删除账号；
- 历史对话：聊天页右上角菜单列出这位角色的所有对话，可只读查看过去那段、删除当前那段以外的旧对话；
- 搭话：显示角色主动发来的消息、未读圆点；搭话模型可以在 App 里填，服务器用 `EIDOLON_SECRET_KEY` 加密保存；
- 推送：设置里的开关默认关；开启后前台服务维持 SSE 长连接，收到搭话弹系统通知，点开进入对应角色对话；关闭开关会立即停止服务；
- 礼物 / 好感度 / 成就 / 每日任务：底部「礼物」页支持抽奖、背包、送礼物、重置卡、每日任务和全角色成就；聊天里显示好感度徽章、升降提示、冷却，以及该角色的成就；
- 统计与分享图：认识天数、来访热力图、好感度分组、成就数量、回合/通话/礼物、排行和 24 小时分布；
- 数据包：设置里导出对话/照片/记忆/档案，或选择 zip 导入；导入前可勾选要恢复的内容；
- 账号安全：退出登录、所有设备退出登录、删除账号；
- 自带密钥按「部署 + 用户」隔离：同一个后端换 IP 或域名仍保留，切换到另一个部署不会串。旧版本按服务器地址保存的密钥，在仍连同一地址时会自动迁移；
- 单元测试：`./gradlew :native:testDebugUnitTest`。

### 还没有的

- 语音通话 / WebRTC（成就里的通话时长会保持 0）；
- 照片实际显示（聊天里只有占位框）；
- 公告、赞助、服务状态、管理后台；
- Google 登录、登录方式绑定/解绑；
- 外观/主题设置；
- 落地页 3D、Spine 立绘。

需要这些功能时可以用网页壳，或者继续往原生 App 里补。

### 服务器地址

原生 App 的 `DEFAULT_SERVER_URL` 在 `android/native/build.gradle`，当前内置公开部署地址；首次启动不需要填写。仍可在「设置 → 服务器」换成别的部署。启动本地栈后，脚本会打印当前局域网 IP；换 Wi-Fi 或 IP 变了，在 App 的「设置 → 服务器」改即可。

## 网页壳（`app`）

```bash
source ~/.config/eidolon-android/env.sh
cd android
./gradlew :app:assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

网页壳的默认地址写在 `android/app/src/main/res/values/strings.xml` 的 `default_server_url`，现在也是空的；输入框的占位地址只是格式示例。它加载的是完整网页版，所以页面、路由和样式都来自 Next.js；它只负责地址、麦克风权限、文件选择、下载、返回键和外部链接。

## 前置条件

- 这台 WSL2 机器和手机在**同一个 Wi-Fi**，且网络没有开启客户端隔离（酒店、访客网络常会隔离）。
- 手机 Android 8+。网页壳建议把「Android System WebView」更新到最新。
- 仓库根目录有 `.env`，公开部署至少要有：

  ```dotenv
  EIDOLON_DOMAIN=eidolon.example.com
  GITHUB_CLIENT_ID=...
  GITHUB_CLIENT_SECRET=...
  ```

  GitHub OAuth 应用的 Redirect URI 填 `https://<域名>/api/auth/github/callback`。
  本地开发也可以改用 `EIDOLON_DEV_LOGIN=1` 和 `EIDOLON_DOMAIN=`（两者不能同时开）。

  文字对话需要 `CHAT_LLM_*` 三项要么一起填，要么全部留空：

  ```dotenv
  CHAT_LLM_PROTOCOL=          # OpenAI 兼容留空；Anthropic 填 anthropic；Responses 填 responses
  CHAT_LLM_BASE_URL=
  CHAT_LLM_API_KEY=
  CHAT_LLM_MODEL=
  ```

  三项全空是**纯自带密钥模式**，在 App 的「设置 → 自带密钥」里填自己的 LLM；只填一部分后端会拒绝启动。

  想用搭话和推送时再加：

  ```dotenv
  EIDOLON_REACH_OUTS=1
  EIDOLON_SECRET_KEY=<用 openssl rand -hex 32 生成>
  ```

  `EIDOLON_SECRET_KEY` 用来加密用户的搭话模型；不设置时 App 里的「搭话模型」无法保存。

  礼物、好感度、成就和每日任务需要：

  ```dotenv
  EIDOLON_AFFINITY_AND_GIFTS=1
  JUDGE_PROVIDER=cloudflare
  CLOUDFLARE_API_TOKEN=...
  CLOUDFLARE_ACCOUNT_ID=...
  ```

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

脚本会打印当前地址，例如：

```text
手机打开：http://<局域网 IP>:8080/
APK 下载：http://<局域网 IP>:8080/apk/
```

实际 IP 以脚本输出为准。要固定或改掉，可以在启动时设置 `EIDOLON_LAN_IP`：

```text
EIDOLON_LAN_IP=<你的局域网 IP> bash scripts/dev-local.sh start
```

## 装到手机

1. 手机浏览器打开 `http://<局域网 IP>:8080/`，确认页面能显示。打不开先看下面的防火墙。
2. 打开 `http://<局域网 IP>:8080/apk/`，选择要装的包：
   - 原生 App Release：`/apk/native/release/native-release.apk`（推荐）
   - 原生 App Debug：`/apk/native/debug/native-debug.apk`
   - 网页壳：`/apk/app-debug.apk`（注意文件名是 `app-debug.apk`，不是 `apk-debug.apk`）
3. 按提示允许浏览器「安装未知应用」。

原生 App 首次启动会显示服务器地址设置；填 Caddy 的地址，登录、同意条款后进入对话列表。网页壳首次启动先显示地址设置，之后是完整网页版：落地页 → 登录 → 开发登录。

原生 App 会自动记住上次的服务器地址；网页壳换地址会清掉旧服务器的 cookie、缓存和 Web Storage。

## 返回键

**原生 App**：

- 聊天页返回对话列表；查看历史对话时，返回先回到当前对话；
- 设置子页（档案、自带密钥、记忆、搭话模型、服务器）返回设置首页；
- 设置和对话列表按返回退出应用。

**网页壳**按页面层级处理返回：

- 顶层页面（`/`、`/conversations`、`/settings`、`/gifts`、`/admin`）按返回退出应用；
- 子页面按返回先回上一级：`/conversation/*` → 对话列表，`/settings/*` → 设置首页，`/admin/*` → 后台首页；`/login`、`/consent`、`/about`、`/privacy`、`/terms` 有历史就回历史，没有就回 `/`；
- 以后开通话时要注意：在通话中的对话页按返回会离开这一页，不会经过网页里的挂断确认。

## 防火墙

WSL2 是 mirrored 网络，Windows 的 WLAN IP 就是 WSL 的 IP，但 Windows 防火墙仍会拦入站。当前 Wi-Fi 配置为 **Public**，用管理员 PowerShell 放行 8080：

```powershell
New-NetFirewallRule -DisplayName "Eidolon dev 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Public
```

用完可以删掉：

```powershell
Remove-NetFirewallRule -DisplayName "Eidolon dev 8080"
```

如果不能提权，退路是 `adb reverse tcp:3000 tcp:3000`，把网页壳的地址改成 `http://localhost:3000`（需要手机开启无线调试并保持 adb 连接）。

## 登录与 Origin

后端在开发模式下只接受 `http://localhost`、`127.0.0.1`、`::1` 的 `Origin`。手机发来的是 `http://<局域网 IP>:8080`，所以 `scripts/dev/Caddyfile` 把上游 `Origin` 改写成 `http://localhost:3000`，同时保留 `Host`。这是本地开发专用配置，公开部署不要照搬。

## 排错

| 现象 | 先看 |
| --- | --- |
| 手机打不开地址 | Windows 防火墙是否放行 8080；手机和电脑是否同一 Wi-Fi；路由器是否隔离客户端 |
| 页面能开，登录返回 403 | Caddy 是否在跑；请求是否绕过了 8080 直连 3000 |
| 后端起不来 | `logs/local-stack/backend.log`；多数是 `CHAT_LLM_*` 只填了一部分，或模型不支持流式/工具调用。三项全空是纯自带密钥模式，后端会正常启动 |
| 原生 App 连不上 | 服务器地址是否是当前局域网 IP；Caddy 是否在跑；通知栏/日志里是否有 401、403 或 429 |
| 原生 App 发不了消息 | 纯自带密钥模式下要在「自带密钥 → 对话 LLM」填 key；没有 key 会收到 429 |
| 收不到搭话推送 | 「设置 → 推送」是否打开、通知权限是否允许；通知栏有没有「Eidolon 正在接收搭话」常驻通知；MIUI 是否允许自启动、省电策略是否「无限制」 |
| 换 IP 后自带密钥不见 | 旧版本按地址保存的密钥需要重填一次；新版本按部署 ID 保存，之后换 IP/域名不会丢 |
| 前端构建失败 | `logs/local-stack/frontend.log`；`next/font/google` 取字体失败时可试 `NODE_OPTIONS=--use-env-proxy` |
| 网页壳布局错乱/白屏 | 更新手机的 Android System WebView；用桌面 Chrome 的 `chrome://inspect` 连调试包排查 |
| 网页壳想换地址 | 先让当前地址加载失败（显示浮层），或清除应用数据；原生 App 直接在「设置 → 服务器」改 |

日志和 PID 都在 `logs/local-stack/`（已被 git 忽略）。

## 安全提醒与已知限制

- 开发登录在局域网内对所有人开放：任何能访问 8080 的人都能冒充任意名字。只在可信网络使用，用完 `bash scripts/dev-local.sh stop`。
- 没有 HTTPS，所以**不能通话**；麦克风需要安全上下文。原生 App 目前也没有通话界面。以后要做通话，可以给 Caddy 配自建 CA 的 HTTPS（并把根证书打进 APK 或装进手机），或者用 `adb reverse` 让页面从 `http://localhost` 加载。
- 原生 App 的推送用前台服务维持 SSE，会有一条低优先级常驻通知；MIUI 仍可能杀进程，需要允许自启动并把省电策略设为「无限制」。
- 原生 App 的照片只显示占位框，还没有实际图片；网页版可以看照片。
- 没有 `corpus/` 时剧情回忆不可用；纯自带密钥且没有部署者向量化/重排/判断模型时，记忆、剧情回忆、好感度、照片也会关闭；不影响文字对话。
- 网页壳的分享图导出用的是 `blob:` 下载，WebView 的下载监听接不住；数据包导入/导出走普通 http(s) 下载，正常。
- Caddy 改写 `Origin` 是本地开发绕过；如果以后经常在局域网上调试，更干净的做法是给后端加一个 `EIDOLON_DEV_ORIGINS` 允许列表并补测试。
