#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
account="${1:?Storage account required}"
[[ "$account" =~ ^[a-z0-9]{3,24}$ ]] || { echo 'Invalid storage account.' >&2; exit 1; }
[[ -d /opt/doan/apps ]] || { echo 'Existing native runtime required.' >&2; exit 1; }
script_dir="$(cd "$(dirname "$0")" && pwd)"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --no-install-recommends -y python3 openjdk-17-jdk-headless maven postgresql-client curl
install -d -m 755 /opt/doan/deploy-agent /var/lib/doan-deploy /var/log/doan-deploy
install -d -o doan -g doan /var/cache/doan-maven
if [[ -d /root/.m2/repository ]]; then
  cp -a -n /root/.m2/repository/. /var/cache/doan-maven/ || true
  chown -R doan:doan /var/cache/doan-maven
fi
install -m 700 "$script_dir/deploy_agent.py" /opt/doan/deploy-agent/deploy_agent.py
install -m 755 /opt/doan/bin/wait-health.sh /opt/doan/deploy-agent/wait-health.sh
printf '{"account":"%s","container":"releases"}\n' "$account" > /opt/doan/deploy-agent/config.json
chmod 600 /opt/doan/deploy-agent/config.json
# Stop the boot orchestrator if installation overlaps its startup sequence.
# Individual Java units remain running until the updater promotes the release.
systemctl stop doan-start.service
cat > /etc/systemd/system/doan-auto-update.service <<'EOF'
[Unit]
Description=Update DoAn backend from latest tested private release
After=network-online.target
Wants=network-online.target
Before=doan-start.service
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
Wants=doan-auto-update.service
After=doan-auto-update.service
[Service]
ExecStart=
ExecStart=/usr/bin/flock /run/doan-deploy.lock /opt/doan/bin/start.sh
EOF
systemctl daemon-reload
systemctl enable doan-auto-update.timer
systemctl start doan-auto-update.service
systemctl start doan-auto-update.timer
echo 'Auto update installed; no VM power action is performed by this installer.'
