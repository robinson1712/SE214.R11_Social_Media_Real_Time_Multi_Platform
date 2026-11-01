#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || { echo 'Run with sudo or Azure RunShellScript.' >&2; exit 1; }
revision="${1:?Supply the reviewed full commit SHA from the SE214 repository}"
[[ "$revision" =~ ^[0-9a-f]{40}$ ]] || { echo 'A full 40-character commit SHA is required.' >&2; exit 1; }
[[ -f /opt/doan/deploy-agent/config.json ]] || { echo 'Existing Azure updater configuration required.' >&2; exit 1; }
account="$(python3 -c 'import json; print(json.load(open("/opt/doan/deploy-agent/config.json"))["account"])')"
[[ "$account" =~ ^[a-z0-9]{3,24}$ ]] || { echo 'Invalid existing storage account.' >&2; exit 1; }
temporary="$(mktemp -d /tmp/se214-startup.XXXXXXXX)"
trap 'rm -rf -- "$temporary"' EXIT
base="https://raw.githubusercontent.com/robinson1712/SE214.R11_Social_Media_Real_Time_Multi_Platform/$revision"
for file in infra/azure/install-auto-update.sh infra/azure/deploy_agent.py infra/no-docker/linux/start.sh infra/no-docker/linux/wait-health.sh; do
  mkdir -p "$temporary/$(dirname "$file")"
  curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
    "$base/$file" --output "$temporary/$file"
done
bash "$temporary/infra/azure/install-auto-update.sh" "$account"
