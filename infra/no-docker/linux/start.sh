#!/usr/bin/env bash
set -euo pipefail
systemctl start doan-kafka
for attempt in {1..30}; do
  if /opt/doan/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
while IFS= read -r topic || [[ -n "$topic" ]]; do
  [[ -z "$topic" ]] && continue
  /opt/doan/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 \
    --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1 >/dev/null
done < /opt/doan/config/topics.txt
# Bring login/routing up before optional feature services, including old inventories.
for name in eureka-server config-server auth-service user-service api-gateway; do
  if ! awk -F '\t' -v target="$name" '$1 == target {found=1} END {exit !found}' /opt/doan/config/services.tsv; then
    echo "Missing core service: ${name}" >&2
    exit 1
  fi
  echo "Starting core ${name}..."
  systemctl start "doan-${name}"
done
systemctl reload-or-restart caddy
echo 'CORE_READY'
failed=''
if [[ -f /etc/systemd/system/doan-alloy.service ]]; then
  systemctl start doan-alloy || failed=' alloy'
fi
while IFS=$'\t' read -r name port || [[ -n "${name:-}" ]]; do
  [[ -z "$name" ]] && continue
  case "$name" in eureka-server|config-server|auth-service|user-service|api-gateway) continue ;; esac
  echo "Starting ${name}..."
  if ! systemctl start "doan-${name}"; then
    failed+=" ${name}"
  fi
done < /opt/doan/config/services.tsv
if [[ -n "$failed" ]]; then
  echo "BACKEND_DEGRADED:${failed}" >&2
  exit 1
fi
echo 'BACKEND_READY'
