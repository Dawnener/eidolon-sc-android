# 阿里云 ECS / VPS 部署清单

面向 2 核 2G、固定 3 Mbps、几个人使用的自托管部署。所有命令都在仓库根目录执行。

## 1. 实例与安全组

- 公网 IPv4：必须。没有公网 IP 时 coturn 无法工作。
- 安全组入方向：
  - TCP 80、443：Caddy
  - TCP 3478、UDP 3478：coturn
  - UDP 49160–49259：coturn 中继端口
- 磁盘 40G 起步；立绘素材约 1.6G。

## 2. 系统准备（Ubuntu / Debian）

```bash
# 2G 内存建议加 2G swap
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Docker 和 compose 插件
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
```

重新登录后确认：

```bash
docker compose version
```

## 3. 代码与素材

把仓库拉到服务器，确认这些目录存在：

```text
.env
corpus/stand/source/assets/spine/...
corpus/stand/outfits/...
corpus/stand/thumbnails/...
```

如果素材还在旧机器上，先把整个 `corpus/stand` 传过来。3M 上行传 1.6G 会比较久，建议用 OSS 或压缩后 scp。

## 4. `.env`

```bash
cp .env.example .env
```

再把 [deploy/ecs.env.example](../deploy/ecs.env.example) 里的项合并进 `.env`，至少填：

- `EIDOLON_DOMAIN`
- `EIDOLON_PUBLIC_IP`
- `EIDOLON_PRIVATE_IP`
- `EIDOLON_TURN_SECRET`（`openssl rand -hex 32`）
- `EIDOLON_CONCURRENT_CALLS=2`
- `EIDOLON_STAND_ASSETS_URL=https://你的域名/stand-assets`

以及 `.env.example` 里的 OAuth、数据库、备份等密钥。

## 5. 镜像

生产 compose 默认用 GHCR 上游镜像。如果你的 fork 改了后端/前端，必须使用自己构建的镜像：

```bash
# 在内存足够的机器上构建，或在本机构建后推到自己的 GHCR
docker compose -f docker-compose.prod.yml build

# 推到自己仓库后，在 .env 里指定
EIDOLON_BACKEND_IMAGE=ghcr.io/你的账号/eidolon-sc-backend
EIDOLON_FRONTEND_IMAGE=ghcr.io/你的账号/eidolon-sc-frontend
EIDOLON_IMAGE_TAG=main
```

2G 机器上直接 `next build` 容易 OOM；能本地构建就不要在 ECS 上构建。

## 6. 启动

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

查看状态：

```bash
docker compose -f docker-compose.prod.yml ps
docker stats --no-stream
free -h
```

## 7. TURN / 通话验收

确认后端返回了公网 IP 的 TURN：

```bash
curl -s https://你的域名/api/health
curl -s -H "Cookie: ..." "https://你的域名/api/ice-servers?conversation=..." | jq
```

返回的 `iceServers` 里应该有：

```json
{"urls":["turn:你的EIP:3478?transport=udp","turn:你的EIP:3478?transport=tcp"]}
```

在服务器上测 coturn：

```bash
docker compose -f docker-compose.prod.yml logs coturn | tail -50
```

手机切到移动网络实际打一通；如果 ICE 一直失败，先检查安全组 UDP 和 `EIDOLON_PUBLIC_IP`、`EIDOLON_PRIVATE_IP`。

## 8. 说明

- TURN 必须直连公网 IP，不能经过 Cloudflare 代理域名。
- 3 Mbps 是整机共享：有人在下载 APK/立绘时会抢占通话带宽。
- 2G 内存不要按默认 10 路并发跑；先设 `EIDOLON_CONCURRENT_CALLS=2`。
- 大陆 ECS 直接用域名提供 80/443 需要 ICP 备案；只做 TURN 或走 Cloudflare Tunnel 时按对应方式处理。
