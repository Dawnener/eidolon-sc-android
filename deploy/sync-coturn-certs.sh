#!/bin/sh
# 把 Caddy 自动续期的证书同步给 coturn，供 TURN over TLS（5349）使用。
#
# Caddy 的证书在 caddy-data 卷里，只有 root 读得到；coturn 以 nobody 运行，
# 所以复制一份到 deploy/coturn-certs（不进仓库），属组设为 nogroup，权限 640。
# 证书更新后重启 coturn，让它重新读文件。建议放进 root 的 cron，每天跑一次。
#
#   sudo deploy/sync-coturn-certs.sh
#
# coturn 未启用 TLS、或证书还没签发时，脚本以 0 退出，不重启容器。
set -eu

root=$(cd -- "$(dirname -- "$0")/.." && pwd)
domain=$(sed -n 's/^EIDOLON_DOMAIN=//p' "$root/.env" | tail -n 1 | tr -d '\r')
[ -n "$domain" ] || { echo "没有在 .env 里找到 EIDOLON_DOMAIN" >&2; exit 1; }

volume=$(docker volume ls -q | grep -E '_caddy-data$' | head -n 1 || true)
[ -n "$volume" ] || { echo "找不到 caddy-data 卷" >&2; exit 1; }
mount=$(docker volume inspect "$volume" --format '{{.Mountpoint}}')
source_dir="$mount/caddy/certificates/acme-v02.api.letsencrypt.org-directory/$domain"
if [ ! -f "$source_dir/$domain.crt" ] || [ ! -f "$source_dir/$domain.key" ]; then
  echo "证书还没签发：$source_dir" >&2
  exit 0
fi

destination="$root/deploy/coturn-certs"
mkdir -p "$destination"
changed=0
for pair in "$domain.crt:fullchain.pem" "$domain.key:privkey.pem"; do
  from=${pair%%:*}
  to=${pair##*:}
  if ! cmp -s "$source_dir/$from" "$destination/$to"; then
    cp "$source_dir/$from" "$destination/$to"
    chown root:65534 "$destination/$to"
    chmod 640 "$destination/$to"
    changed=1
  fi
done

if [ "$changed" = 1 ]; then
  docker restart "$(docker ps -q -f name=coturn | head -n 1)" >/dev/null
  echo "证书已更新，coturn 已重启"
else
  echo "证书没有变化"
fi
