#!/usr/bin/env bash
set -euo pipefail
port="$1"
deadline=$((SECONDS + ${2:-180}))
while (( SECONDS < deadline )); do
  if curl --fail --silent --max-time 3 "http://127.0.0.1:${port}/actuator/health" >/dev/null; then
    exit 0
  fi
  sleep 2
done
echo "Service on port ${port} did not become healthy." >&2
exit 1
