#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
account="${1:?Storage account required}"
[[ "$account" =~ ^[a-z0-9]{3,24}$ ]] || { echo 'Invalid storage account.' >&2; exit 1; }
[[ -d /opt/doan/apps ]] || { echo 'Existing native runtime required.' >&2; exit 1; }
script_dir="$(cd "$(dirname "$0")" && pwd)"
systemctl stop doan-auto-update.timer 2>/dev/null || true
exec 9>/run/doan-deploy.lock
if ! flock -n 9; then
  systemctl start doan-auto-update.timer
  echo 'Startup/deployment is busy. Let it finish before installing boot changes.' >&2
  exit 1
fi
boot_state="$(systemctl show doan-start.service -p ActiveState --value 2>/dev/null || true)"
case "$boot_state" in
  activating|deactivating)
    systemctl start doan-auto-update.timer
    echo 'Native startup is busy. Let it finish before installing boot changes.' >&2
    exit 1
    ;;
esac
updater_state="$(systemctl show doan-auto-update.service -p ActiveState --value 2>/dev/null || true)"
case "$updater_state" in
  active|activating|deactivating)
    systemctl start doan-auto-update.timer
    echo 'Updater is busy. Let the current deployment finish before installing boot changes.' >&2
    exit 1
    ;;
esac
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --no-install-recommends -y python3 openjdk-17-jre-headless postgresql-client curl
install -d -m 755 /opt/doan/deploy-agent /var/lib/doan-deploy /var/log/doan-deploy
install -d -m 755 /opt/doan/bin
install -m 700 "$script_dir/deploy_agent.py" /opt/doan/deploy-agent/deploy_agent.py
install -m 755 "$script_dir/../no-docker/linux/start.sh" /opt/doan/bin/start.sh
install -m 755 "$script_dir/../no-docker/linux/wait-health.sh" /opt/doan/bin/wait-health.sh
install -m 755 /opt/doan/bin/wait-health.sh /opt/doan/deploy-agent/wait-health.sh
printf '{"account":"%s","container":"releases"}\n' "$account" > /opt/doan/deploy-agent/config.json
chmod 600 /opt/doan/deploy-agent/config.json
cat > /etc/systemd/system/doan-auto-update.service <<'EOF'
[Unit]
Description=Update DoAn backend from latest tested private release
After=network-online.target doan-start.service
Wants=network-online.target
[Service]
Type=oneshot
ExecStart=/usr/bin/python3 /opt/doan/deploy-agent/deploy_agent.py
TimeoutStartSec=3600
StandardOutput=journal
StandardError=journal
EOF
cat > /etc/systemd/system/doan-auto-update.timer <<'EOF'
[Unit]
Description=Check backend releases while VM is running
[Timer]
OnBootSec=30
OnUnitInactiveSec=120
Unit=doan-auto-update.service
[Install]
WantedBy=timers.target
EOF
install -d /etc/systemd/system/doan-start.service.d
cat > /etc/systemd/system/doan-start.service.d/auto-update.conf <<'EOF'
[Unit]
StartLimitIntervalSec=900
StartLimitBurst=3
[Service]
ExecStart=
ExecStart=/usr/bin/flock /run/doan-deploy.lock /opt/doan/bin/start.sh
RestartSec=60
EOF
# Apply the same bounded recovery policy to already-installed JARs.
units=()
while read -r name port; do
  [[ -z "$name" ]] && continue
  [[ "$name" =~ ^[a-z][a-z0-9-]+$ && "$port" =~ ^[0-9]+$ ]] || { echo 'Invalid service inventory.' >&2; exit 1; }
  case "$name" in start|auto-update|kafka|alloy|cloud-check|deploy-agent) echo 'Reserved service name.' >&2; exit 1 ;; esac
  install -d -m 755 "/etc/systemd/system/doan-${name}.service.d"
  cat > "/etc/systemd/system/doan-${name}.service.d/startup-policy.conf" <<EOF
[Unit]
StartLimitIntervalSec=900
StartLimitBurst=3
[Service]
ExecStartPost=
ExecStartPost=/opt/doan/deploy-agent/wait-health.sh \$SERVER_PORT 300
Restart=on-failure
RestartSec=60
TimeoutStartSec=360
EOF
  rm -f "/run/systemd/system/doan-${name}.service.d/99-se214-recovery.conf"
  units+=("doan-${name}.service")
done < /opt/doan/config/services.tsv
systemctl daemon-reload
systemctl reset-failed doan-start.service "${units[@]}"
systemctl enable doan-start.service doan-auto-update.timer
flock -u 9
exec 9>&-
systemctl restart doan-start.service
systemctl start doan-auto-update.timer
echo 'Boot-first startup installed. CI-built releases are checked after native boot completes.'
