#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
[[ -d /opt/doan/apps && -d /opt/doan/env ]] || { echo 'Extract the private runtime package under /opt/doan first.' >&2; exit 1; }
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --no-install-recommends -y openjdk-17-jre-headless curl ca-certificates caddy
if [[ -f /opt/doan/source.tar ]]; then
  apt-get install --no-install-recommends -y openjdk-17-jdk-headless maven unzip
  bash /opt/doan/bin/build-runtime.sh
fi
id doan >/dev/null 2>&1 || useradd --system --home-dir /opt/doan --shell /usr/sbin/nologin doan
install -d -o doan -g doan /opt/doan/data/kafka /opt/doan/logs
chown -R doan:doan /opt/doan
chmod 700 /opt/doan/env
chmod 600 /opt/doan/env/*.env
chmod +x /opt/doan/bin/*.sh /opt/doan/kafka/bin/*.sh
if [[ -f /opt/doan/bin/alloy ]]; then
  chmod +x /opt/doan/bin/alloy
  install -d -o doan -g doan /opt/doan/data/alloy
fi
if [[ ! -f /opt/doan/data/kafka/meta.properties ]]; then
  cluster_id=$(runuser -u doan -- /opt/doan/kafka/bin/kafka-storage.sh random-uuid)
  runuser -u doan -- /opt/doan/kafka/bin/kafka-storage.sh format \
    -t "$cluster_id" -c /opt/doan/config/kafka.properties
fi
install -m 644 /opt/doan/systemd/*.service /etc/systemd/system/
install -m 644 /opt/doan/config/Caddyfile /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
cat > /etc/logrotate.d/doan <<'EOF'
/opt/doan/logs/*.log {
    daily
    rotate 7
    size 20M
    compress
    missingok
    notifempty
    copytruncate
    su doan doan
}
EOF
systemctl daemon-reload
systemctl enable doan-start caddy
systemctl start doan-start
echo 'Native Java runtime started. Check systemctl status doan-start and HTTPS.'
