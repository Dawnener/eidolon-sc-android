# 本地验收原生 App

网页前端已经移除，客户端只有 `android/native/`。票里要求「本地构建版上验收」时照这里做：在本机的 Android 模拟器（或同一局域网的手机）上装构建版，连本机开发栈。截图用 `adb` 拍，写到 `.scratch/_accept/`，验完删掉，不提交。

## 起服务、停服务

```sh
bash scripts/dev-local.sh start     # 后端 :8000、Caddy :8080
bash scripts/dev-local.sh status
bash scripts/dev-local.sh stop
```

后端在 `:8000`，Caddy 在 `:8080`，日志在 `logs/local-stack/`。`.env` 里要有 `EIDOLON_DEV_LOGIN=1`，`EIDOLON_DOMAIN` 留空，两者不能同时开。

停服务后按端口确认进程真的退了，再重新构建或改数据库：

```sh
ss -ltnp 'sport = :8000 or sport = :8080'
```

耗时的活（Gradle 构建、后端全量 pytest、缩略图渲染）一次只跑一个，别和别的任务抢 CPU。

## 构建、安装

```sh
source ~/.config/eidolon-android/env.sh
cd android
./gradlew :native:assembleDebug
adb install -r native/build/outputs/apk/debug/Eidolon-SC-*-debug.apk
```

- 模拟器：`~/.local/opt/eidolon-android/sdk/emulator/emulator -avd eidolon-test &`，等 `adb wait-for-device` 和开机完成再装。
- 只改了 Java/Kotlin 或资源时 `assembleDebug` 是增量的；单元测试用 `./gradlew :native:testDebugUnitTest`。
- 要从头走首次启动、同意条款和登录，先 `adb shell pm clear sc.eidolon.nativeapp`。

## 服务器地址

- 模拟器访问本机用 `http://10.0.2.2:8080`。
- 真机用 `scripts/dev-local.sh start` 打印的局域网地址，例如 `http://192.168.x.x:8080`。

在「设置 → 服务器」里改。换地址会清掉原来的登录状态，需要重新登录。Caddy 的 `:8080` 同时提供 `/api`、`/stand-assets` 和 APK 下载页。

## 登录

登录页的「开发登录」按名字直接登录（用户不存在就创建），只有服务器开了开发登录时才出现。本地库里 `Sallyn` 是管理者；验收专用的状态改一个单独的用户（例如 `gift-accept`），不要动 `Sallyn` 的数据。

第一次登录要同意隐私政策和使用条款、声明年满 13 岁。本地验收不需要 OAuth 应用。

## 麦克风与通话

先授予录音权限，免得系统弹窗挡着：

```sh
adb shell pm grant sc.eidolon.nativeapp android.permission.RECORD_AUDIO
```

模拟器里在 Extended controls → Microphone 选「Virtual microphone uses host audio input」，或在 Host audio 里选本机的输入设备。

打电话后依次检查：

- 拨号中 → 接通，计时开始；
- 服务器配了立绘时显示 Spine 立绘，右上角衣架能换衣装；
- 她说日语时台词和中文字幕逐句出现，立绘有口型和表情；
- 静音、免提切换有效；挂断后出现结束卡。

启动失败会写 `EidolonCall` 日志：

```sh
adb logcat -s EidolonCall
```

本地 Fish 直连不通时，接通了也没声音，后端日志里会有 `FishTTS … timed out during opening handshake`。临时在 `.env` 里加上本机代理，`stop` 再 `start`，验收完删掉这两行：

```dotenv
EIDOLON_PROXY_URL=http://127.0.0.1:10808
EIDOLON_PROXY_SERVICES=judge,tts
```

地址和端口按本机实际代理改。模拟器的假麦克风只发提示音，听不出她的声音；要听声音用同一 Wi-Fi 的真机打。

## 造出结束卡

原生 App 没有 Playwright 的请求拦截，用后端状态造：

| 要看到的 | 怎么造 |
|---|---|
| 没接通、按钮「重试」 | 拨号前 `bash scripts/dev-local.sh stop`，拨完再 `start` |
| 超限额 | 临时在 `.env` 加 `EIDOLON_CALLS_PER_HOUR=1`，`stop` 再 `start`；先打完一通，再拨第二通 |
| 通话中掉线 | 接通后 `bash scripts/dev-local.sh stop` |
| 到通话时长上限、她挂断 | 临时在 `.env` 加 `EIDOLON_CALL_MINUTES=1`，`stop` 再 `start`，接通后等一两分钟 |

原生 App 的通话是全屏页面，返回即挂断，网页版「换到别的页面后通话因故结束」的场景不适用。

## 造状态：礼物、好感度、每日任务

用专用用户 `gift-accept` 在 App 里开发登录、同意条款，打开一次礼物页让后端建好行。然后停后端，直接改本机数据库：

```sh
bash scripts/dev-local.sh stop
cd backend
uv run python - <<'PY'
import sqlite3
from datetime import date

db = sqlite3.connect("data/eidolon.db")
user = db.execute("SELECT id FROM users WHERE name = 'gift-accept'").fetchone()[0]
db.execute(
    "INSERT OR REPLACE INTO gift_backpacks (user_id, gift_id, count) VALUES (?, ?, ?)",
    (user, "cake", 3),
)
db.execute(
    "INSERT INTO gift_balances (user_id, draws, given_on) VALUES (?, ?, ?)"
    " ON CONFLICT(user_id) DO UPDATE SET draws = excluded.draws",
    (user, 10, date.today().isoformat()),
)
db.execute(
    "INSERT OR REPLACE INTO affinities"
    " (user_id, character_id, value, updated_at, cooling, gained_today, gained_on)"
    " VALUES (?, ?, ?, ?, 0, 0, NULL)",
    (user, "mano", 80, "2026-10-23T12:00:00+00:00"),
)
db.commit()
PY
bash scripts/dev-local.sh start
```

礼物 id 和名字在 `characters/gifts.toml`，角色 id 在 `characters/characters.toml`。验完以 `gift-accept` 登录，在「设置 → 删除账号」里删掉它，数据一起删除，`Sallyn` 不受影响。

## 截图

```sh
mkdir -p .scratch/_accept
adb exec-out screencap -p > .scratch/_accept/call-ended.png
```

验收完 `bash scripts/dev-local.sh stop`，删掉 `.scratch/_accept/`。
