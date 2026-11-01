# Cloud no-Docker audit handoff — 2026-09-30

## Request and safety constraints

Continue the audit and testing in a new Codex session using GPT-6 Astra with medium reasoning. The user authorizes tests/builds and fixes to confirmed defects. Preserve all existing tracked and untracked changes in this shared checkout. Do not reset, restore, revert, commit, deploy, or use Docker. Do not edit historical `VERIFICATION_REPORT.md`. Use apply_patch for edits. Do not disclose `.runtime/env.ps1` secrets. Check applicable AGENTS.md. No additional agents are needed. Communicate progress in Vietnamese.

## Evidence provenance

The following test results and implementation details come from the preceding model's handoff summary; the session preparing this file only confirmed the dirty working tree and presence of the named new tests. Recheck source and existing reports before relying on them. No final audit report exists yet. This handoff is not cloud acceptance.

## Work already implemented

Existing work spans native cloud configuration and launch scripts, native PostgreSQL CI, Gateway JWT/header validation, STOMP guards, content authorization, S3 storage, and documentation. Read PLAN.md, NO_DOCKER_SETUP.md, ACCEPTANCE_MATRIX.md and relevant diffs.

Public registration creates USER only. Internal admin bootstrap uses X-Admin-Bootstrap-Token, disabled when unconfigured, minimum 32 UTF-8 bytes and constant-time matching; SERIALIZABLE transaction rejects existing ADMIN and duplicate email. AdminEmailAllowlist was removed. Launcher passes bootstrap secret only to auth-service. Docker auth host port was changed to loopback.

Audit changes reported by predecessor:

- AuthService publishes registration event and bootstrap success audit only after commit using TransactionSynchronization. This is not a durable outbox.
- PostService.searchPublicPosts filters visibility anonymously to exclude private-group posts; search-service does not propagate viewer identity. Pagination counts/underfilled pages remain limitations.
- Chat and notification WebSocket config honors CORS_ALLOWED_ORIGINS, uses explicit allowed origins and rejects wildcards. New WebSocketOriginTest in both modules.
- Local cloud-free profiles bind all 16 business services to 127.0.0.1 and use Eureka hostname with prefer-ip-address false, even without optional Config Server. Search profile was newly added.

## Test evidence inherited

- Escalated Maven auth reactor test succeeded: common 14 and then-existing auth 15 tests passed.
- Subsequent escalated offline full `-B -o package` succeeded across all 21 reactor entries, packaging 19 service JARs. New bootstrap unit tests passed. Group native PostgreSQL tests: 5 skipped because RUN_NATIVE_POSTGRES_TESTS was absent. Aggregate totals need collection from Surefire XML.
- That full build predates the post search regression, WebSocket origin tests, local profile changes, and AdminBootstrapPostgresTest. These require final compilation/tests.
- No native PostgreSQL executable/service was found. Maven is not on PATH. Use JDK 21 (target 17); CI uses Java 17. Prior Maven sandbox cache access failed, then escalated execution succeeded without cache repair. No Maven command is reported running.

PowerShell tool paths:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
& 'C:\Users\ADMIN\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' -B -o clean package
```

## Next actions

1. Inspect newest changes/tests, especially AdminBootstrapPostgresTest. It is gated by RUN_NATIVE_POSTGRES_TESTS=true, uses disposable auth_bootstrap_test with create-drop, mocks encoder/JWT/Kafka, and coordinates two SERIALIZABLE bootstrap calls. Tighten its catch of arbitrary RuntimeException so unrelated failures cannot count as the expected race loser. Never target a real database.
2. CI now creates auth_bootstrap_test and labels 19 services plus common library; CI has not run. Run final clean package, fix confirmed defects, report exact totals and skips. Use proper escalation if sandbox blocks dependency/cache access.
3. Audit PowerShell syntax and sample environment mappings without exposing real secrets. Inspect profile consistency. Consider missing negative authorization and HTTP contract tests.
4. Review remaining potential issues: public media URLs bypass private ACL; story author-list and markViewed may bypass friend visibility; STOMP bucket removal on disconnect may refill burst on reconnect; JWT defaults/issuer/audience/revocation, SQL role isolation/TLS, live expiry/SockJS, and filtered search counts remain open. Confirm before declaring findings.
5. Write new AUDIT_REPORT_2026-09-30.md with actual commands, counts, skips, environment, confirmed fixes, unresolved risks and cloud/browser/ARM64 acceptance still pending. Leave historical VERIFICATION_REPORT.md untouched.
6. Update PLAN/setup/acceptance references that still claim tests were never rerun. Do not mark live cloud acceptance PASS based on local tests/builds.
7. Review git diff --check and final status, preserving all previous changes. Final Vietnamese report should link new report and distinguish verified local results from remaining acceptance work.
