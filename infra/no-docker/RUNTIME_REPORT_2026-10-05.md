# Supabase verification — 2026-10-05

Project: `oksedozcqzjxnpujlqeh`. Credentials are loaded from the ignored `.runtime/env.ps1`; no passwords are recorded here.

Follow-up: the MongoDB connection blocker and subsequent startup issues were resolved on 2026-10-06. See [the latest runtime report](RUNTIME_REPORT_2026-10-06.md). The sections below record the earlier diagnostics.

## Resolved

- The first connection attempt returned `28P01`; a later attempt using the runtime environment authenticated successfully. The later result establishes that the credentials now work, without identifying why the first attempt failed.
- After authentication, all 10 PostgreSQL services returned `3F000` because their application schemas were absent.
- The schema initialization script initially returned `42501`. Its role membership grant occurred after `CREATE SCHEMA ... AUTHORIZATION`; moving that grant before schema creation fixed the execution. The failed initialization was rolled back.
- The corrected repository SQL was committed successfully on Supabase using `Initialize-Supabase.ps1`. It creates roles/schemas and configures privileges; it does not delete business data or change passwords.

## Verification

`Test-CloudConnections.ps1 -SupabaseOnly` exited 0. Auth, user, media, post, comment, reaction, group, fanpage, dating and moderation services each returned `PASS login/schema USAGE/CREATE`.

This verifies database login and schema permissions. It does not establish that every business service has started, that CRUD flows work, or that a public deployment is ready.

## Remaining MongoDB connection blocker

After the user updated the four MongoDB URIs, the full check no longer detected credential placeholders. Supabase (10 services), Valkey AUTH/PING and Storage HeadBucket pass. All four MongoDB probes fail with `MongoTimeoutException`; driver server errors are `MongoSocketWriteException/SSLException`.

DNS SRV/TXT resolution succeeds and a subsequent TCP-only test connects to all three Atlas nodes on port 27017. TLS negotiation fails before database authentication, so the MongoDB password has not yet been verified. The user confirmed that the current client IP has not been added to the Atlas IP access list, or the entry remains pending. Add that IP and wait for Active, then rerun the connection check. Business service startup is still pending.
