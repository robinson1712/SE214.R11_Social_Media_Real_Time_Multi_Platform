# No-Docker Verification Report

Verified on Windows on 2026-09-16. This report separates implementation checks from cloud
acceptance that requires real project credentials.

## Completed

- Downloaded pinned Java 17.0.20.1, Kafka 3.7.0, Caddy 2.11.4 and Alloy 1.18.0 from official
  release URLs; every archive passed its published SHA-256 or SHA-512 digest.
- Re-ran `Setup.ps1` successfully without downloading or replacing installed tools.
- Parsed all PowerShell launch scripts with the Windows PowerShell parser.
- Built all 21 Maven reactor modules and produced all 19 executable runtime JARs.
- Ran the final regular backend test reactor: 455 tests passed with no failures or errors.
- Ran the final media test reactor after adding the Supabase endpoint-path check: 12 common
  tests and 24 media tests passed.
- Started Kafka in KRaft mode with the native Java command, opened port 9092, created and listed
  `runtime-smoke-test`, then stopped the managed PID successfully.
- Started Eureka and Config Server, verified both health endpoints, and fetched the shared
  `cloud-free` configuration from Config Server.
- Verified the packaged media JAR contains `object-storage.provider=s3` and that AWS SDK accepts
  the Supabase `/storage/v1/s3` endpoint path.
- Validated the Caddyfile with Caddy and formatted/validated the Alloy configuration with Alloy.
- Confirmed no managed process or runtime port remained after the smoke tests.

## Disk measurement

The reported Docker footprint of more than 30 GB is the user-observed baseline and was not
deleted or re-measured by these scripts.

| Category | Measured size |
|---|---:|
| Downloaded no-Docker runtime tools | 711.7 MB |
| Runtime footprint after smoke tests | 0.754 GB |
| Source plus Maven build output on the development machine | 1.641 GB |
| Runtime target | no more than approximately 10 GB |

Maven caches and SDKs are development dependencies, not part of the runtime bundle. The CI bundle
script independently fails the build if packaged output exceeds 10 GB.

## Pending external acceptance

The following checks need real Supabase, Atlas, Aiven and Grafana Cloud credentials and therefore
were not claimed as passed:

- Full `Doctor.ps1` cloud reachability checks.
- Database transaction/schema isolation, Mongo TTL indexes and Valkey command compatibility.
- Supabase Storage upload, public read, delete, video Range request and quota behavior.
- All business, realtime and observability scenarios in `ACCEPTANCE_MATRIX.md`.
- Three complete restart cycles and the two-user 60-minute run.
- Local Flutter Web build; Flutter is not installed on this machine. GitHub Actions builds it and
  assembles the complete runtime artifact.

Do not make `cloud-free` the default profile until these external acceptance items pass.
