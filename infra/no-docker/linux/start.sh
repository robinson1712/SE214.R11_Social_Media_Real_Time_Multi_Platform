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
if [[ -f /etc/systemd/system/doan-alloy.service ]]; then
  systemctl start doan-alloy
fi
while IFS=$'\t' read -r name port || [[ -n "${name:-}" ]]; do
  [[ -z "$name" ]] && continue
  echo "Starting ${name}..."
  systemctl start "doan-${name}"
done < /opt/doan/config/services.tsv
systemctl reload-or-restart caddy
