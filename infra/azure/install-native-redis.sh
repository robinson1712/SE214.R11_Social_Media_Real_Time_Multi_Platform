#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
script_dir="$(cd "$(dirname "$0")" && pwd)"
if command -v redis-server >/dev/null && [[ ! -f /opt/doan/env/native-redis.env ]]; then
  echo 'Existing unmanaged Redis detected; inspect it before adopting native Redis.' >&2
  exit 1
fi
exec 9>/run/doan-deploy.lock
flock -n 9 || { echo 'Startup/deployment is busy; native Redis configuration deferred.' >&2; exit 1; }
boot_state="$(systemctl show doan-start.service -p ActiveState --value 2>/dev/null || true)"
case "$boot_state" in
  activating|deactivating)
    echo 'Native startup is busy; native Redis configuration deferred.' >&2
    exit 1
    ;;
esac
if ! command -v redis-server >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install --no-install-recommends -y redis-server redis-tools
fi
python3 "$script_dir/configure_native_redis.py"
systemctl daemon-reload
systemctl enable redis-server.service
systemctl restart redis-server.service
echo 'Native Redis installed: loopback, AUTH, AOF. Backend reads the private override on next service start.'
