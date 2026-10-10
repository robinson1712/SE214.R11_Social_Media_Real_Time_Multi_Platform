#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
flutter_version="3.47.5"
flutter_root="${repo_root}/.runtime/tools/flutter-linux"

# Only the public backend origin is passed to Flutter; backend env is not loaded.
if [[ "${BACKEND_PENDING:-false}" != "true" ]]; then
node <<'NODE'
let url;
try { url = new URL(process.env.API_BASE_URL); }
catch { throw new Error('Set API_BASE_URL to the real public HTTPS backend origin in Cloudflare build variables.'); }
if (url.protocol !== 'https:' || url.username || url.password ||
    url.pathname !== '/' || url.search || url.hash ||
    ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) {
  throw new Error('API_BASE_URL must be a public HTTPS origin without credentials, path or query.');
}
NODE
fi

if [[ ! -x "${flutter_root}/bin/flutter" ]]; then
  mkdir -p "$(dirname "${flutter_root}")"
  git clone --depth 1 --branch "${flutter_version}" https://github.com/flutter/flutter.git "${flutter_root}"
fi

cd "${repo_root}/frontend"
"${flutter_root}/bin/flutter" config --no-analytics
"${flutter_root}/bin/flutter" pub get --enforce-lockfile
"${flutter_root}/bin/flutter" build web --release \
  --dart-define="API_BASE_URL=${API_BASE_URL:-}" \
  --dart-define="BACKEND_PENDING=${BACKEND_PENDING:-false}"
cp "${repo_root}/infra/cloudflare/_headers" build/web/_headers
