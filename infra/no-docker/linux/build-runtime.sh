#!/usr/bin/env bash
set -euo pipefail
mkdir -p /opt/doan/source /opt/doan/downloads
tar -xf /opt/doan/source.tar -C /opt/doan/source
cd /opt/doan/source
export MAVEN_OPTS='-Xmx768m'
# The same source already passed CI tests. Compile/package on the target JVM.
mvn -B -Pnative-runtime clean package -DskipTests > /opt/doan/build.log 2>&1
while IFS=$'\t' read -r name port || [[ -n "${name:-}" ]]; do
  [[ -z "$name" ]] && continue
  jar=$(find /opt/doan/source -path "*/.runtime/build/${name}.jar" -type f -print -quit)
  [[ -n "$jar" ]] || { echo "Missing runtime JAR: ${name}" >&2; exit 1; }
  install -m 644 "$jar" "/opt/doan/apps/${name}.jar"
done < /opt/doan/config/services.tsv
cd /opt/doan/downloads
curl --fail --location --retry 3 --silent --show-error \
  https://archive.apache.org/dist/kafka/3.7.0/kafka_2.13-3.7.0.tgz -o kafka.tgz
curl --fail --location --retry 3 --silent --show-error \
  https://archive.apache.org/dist/kafka/3.7.0/kafka_2.13-3.7.0.tgz.sha512 -o kafka.sha512
# Apache checksum files use a decorated multi-line representation.
expected=$(sed 's/^[^:]*: //' kafka.sha512 | tr -cd '0-9a-fA-F' | tr 'A-F' 'a-f')
actual=$(sha512sum kafka.tgz | cut -d ' ' -f 1)
[[ "$expected" == "$actual" ]] || { echo 'Kafka checksum mismatch.' >&2; exit 1; }
tar -xf kafka.tgz -C /opt/doan/kafka --strip-components=1
if [[ -f /opt/doan/config/alloy.sha256 ]]; then
  curl --fail --location --retry 3 --silent --show-error \
    https://github.com/grafana/alloy/releases/download/v1.18.0/alloy-linux-amd64.zip -o alloy.zip
  echo "$(cat /opt/doan/config/alloy.sha256)  alloy.zip" | sha256sum --check --status
  unzip -o -q alloy.zip alloy-linux-amd64 -d /opt/doan/bin
  mv /opt/doan/bin/alloy-linux-amd64 /opt/doan/bin/alloy
fi
echo 'Target Java JARs and native Kafka/Alloy prepared.'
