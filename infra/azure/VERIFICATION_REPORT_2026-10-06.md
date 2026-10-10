# Cloud deployment verification — 06/10/2026

## Verified

- Azure for Students subscription Enabled; spending limit On. No paid upgrade.
- Native Ubuntu x64 VM E2as v4, 2 CPU/16 GB, Central India. EASv5 quota was 0; B4ms attempts failed capacity validation before creating a VM. EASv4 was successfully provisioned.
- Target JDK 17 Maven reactor build PASS. Backend source was committed at `d23751a`; current uncommitted deploy scripts were supplied separately. The target package skipped repeating tests; existing CI/local test evidence remains separate.
- Source package was approximately 200 KB. A 1.8 GB fat-JAR upload was canceled after about 30 MB; source build completed in about 1 minute 27 seconds on VM.
- Native Kafka and Alloy downloads verified using Apache SHA512 and GitHub SHA256 respectively.
- VM cloud probe: 10 Supabase login/schema USAGE/CREATE PASS, 4 MongoDB login/read PASS, Valkey AUTH/PING PASS, Storage authenticated HeadBucket PASS.
- All 19 Java actuator health endpoints UP on VM loopback. Kafka, Alloy and Caddy active; boot units for orchestration/Caddy enabled.
- All Java/Kafka/Alloy listeners verified on loopback. NSG opens 80/443 and restricts SSH to workstation IP.
- Trusted HTTPS backend origin resolved to VM IP and passed certificate verification from workstation.
- Protected posts 401; empty login 400; nonexistent login 401; Pages CORS preflight 200 with exact origin; untrusted origin 403; public actuator 404; unauthenticated WebSocket path 401.
- Login error response also included exact Pages CORS origin, so browser can read API validation errors.
- Pages production configured with the real API origin and `BACKEND_PENDING=false`.
- Pages deployment `65e16735-bd33-4c1f-8ead-d0b36669ab8d` for commit `0d3fc453800f1f76e667496889f46100c6bae4f6` finished successfully. Public `/login` and `main.dart.js` returned HTTP 200; deployed JavaScript contains the real Azure backend hostname.
- Windows CI runtime artifacts measured about 1.61 GB each. New Windows bundles are now opt-in on manual dispatch with one-day retention; automatic pushes retain backend tests and Pages Git integration. Previously uploaded artifacts were not deleted.

Evidence on workstation: `.runtime/state/azure-health.json`, `.runtime/state/azure-https-smoke.json`, `.runtime/state/azure-vm.json`. VM connection-check log: `/opt/doan/logs/cloud-check.log`; build log: `/opt/doan/build.log`. These logs are not published frontend assets.

## Limits

Actual authenticated browser login, CRUD, media upload/privacy, authenticated STOMP reconnect and a complete VM deallocate/start cycle have not yet been exercised in this deployment report. Backend uptime depends on VM running and student credit being available. Later documentation-only pushes do not alter the verified frontend code.
