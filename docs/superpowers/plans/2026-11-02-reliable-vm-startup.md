# Reliable VM startup implementation plan

> **For agentic workers:** Use executing-plans inline; changes are one deployment pipeline, with one independent review at the end. Track the steps below.

**Goal:** Starting the existing VM brings up the installed backend without compiling it or waiting for a release download.

**Architecture:** Java 17 JARs are built in GitHub Actions after CI passes and shipped in the existing private release archive. The installed updater consumes those binaries and retains its checksum, database preparation, sequential promotion and rollback. Native boot starts the installed version first; the updater is ordered after boot and uses the same deployment lock.

**Tech stack:** Existing Python 3.12, Bash, Maven, GitHub Actions, Azure Blob managed identity, systemd, Caddy.

**Spec:** User request in this chat: configure the system to be usable after each VM start; incident evidence: 2 CPUs, CPU saturation, repeated Java health timeouts, concurrent on-VM Maven build.

## Constraints

- Keep `ui-ha-tang`, current VM, databases, credentials and public URLs. No new paid infrastructure or dependencies.
- No VM power action during preparation. Commits use the user's selected `ui-ha-tang` / `UI + Hạ tầng` names; PR stays separate from production merge.
- Preserve original workspace edits. Work in the existing managed worktree.
- Existing installed updater does not upgrade itself; document and provide a one-time installer before publishing binary releases.
- Startup still takes time and external database availability remains necessary; do not claim production recovery from local tests.

## Review focus

- Missing or non-executable JAR must fail packaging before publishing.
- Legacy source releases must never invoke Maven in the new VM updater.
- A failed/offline release lookup must not prevent native boot of the installed version.
- Service failure must stop sequential boot and must not retry indefinitely at ten-second intervals.
- Temporary `99-se214-recovery.conf` overrides must be removed when installing the durable policy.

## Tasks

- [x] Run existing Azure test suite as baseline (10 tests).
- [x] Add failing tests for binary archives, VM preparation without compilation and fail-fast native boot.
- [x] Build Java 17 native-runtime JARs in the release job; package and validate every discovered executable artifact.
- [x] Consume prebuilt artifacts on VM, preserve env/schema/rollback behavior, bound restarts and increase startup health budget.
- [x] Prepare boot-first installer and policy for existing services; provide one-time application and verification instructions.
- [x] Run Azure suite (23 passed after Redis/core-first follow-up), real native-runtime Maven package (19 services, application classfile version 61), stream-package all 19 real JARs, Bash syntax/behavior checks and PowerShell parsing.
- [x] Independent review completed; fixed legacy boot concurrency guard and covered the refusal in fixture. Production application/cold boot remains pending.

## Follow-up approved by owner (2026-11-02, Asia/Bangkok)

- Owner approved SSH restricted to current workstation IP; updated existing ssh-admin rule instead of adding public access. Direct logs identified unavailable Aiven Redis DNS as a separate cause of Chat health failure.
- Owner selected native Redis on the same VM and accepted that existing remote Redis data was not migrated. PostgreSQL/MongoDB remain unchanged.
- Added configure/install scripts: loopback, private generated password, AOF, noeviction, 512 MB cap; persistent per-service environment override, including new release units.
- Core-first boot must start Eureka/Config/Auth/User/Gateway before optional services, continue other optional services after an optional failure, and report degradation honestly.
- Installer must restore updater timer even after degraded boot; Redis installer must also reject concurrent legacy startup. Both reviewer findings have regression checks.
- Runtime recovery verified Redis AUTH/no-anonymous access/AOF and Auth/User/Gateway/Chat health UP; Worker POST empty login reaches application validation (400). Full installed boot policy and cold boot remain to verify.
