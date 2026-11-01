# Linux native runtime

These scripts run the existing Java JARs and Kafka under systemd on Ubuntu x64 or ARM64. No Docker is used. The Windows bundle is not copied as an executable Linux runtime.

`Build-LinuxPackage.ps1` exports per-service EnvironmentFiles from the owner's current `.runtime/env.ps1`, copies the built JARs and platform-independent Kafka distribution, and writes systemd units. The resulting package contains credentials and stays under ignored `.runtime/packages`; transfer it only to the intended private VM.

The installer installs Java 17 and Caddy from Ubuntu packages. Alloy is optional; when Grafana is enabled, supply the correct Linux binary with `-AlloyBinary`. Do not use the Windows executable. JVM options and pool budgets follow the validated native runtime. Actual memory/CPU limits still need verification on the selected VM.

The backend hostname must resolve to the VM. Azure Public IP DNS labels can provide a hostname without buying a domain. Open ports 80/443 for Caddy's HTTPS certificate and limit SSH to the administrator's source IP. Java services, Kafka, Eureka and Config Server listen only on loopback. Add the VM's outbound IP to MongoDB Atlas before startup.

```powershell
./infra/no-docker/scripts/Build-LinuxPackage.ps1 -BackendHost 'REAL-AZURE-DNS-HOST' -PagesOrigin 'https://doan1-next.24521533.workers.dev'
```

Extract the private package at `/opt/doan`, then run `sudo /opt/doan/bin/install.sh`. The `doan-start` boot service starts Kafka, initializes missing topics without deleting existing ones, and waits for each Java service health endpoint before continuing. Individual services restart after crashes. Logs are bounded by logrotate.

When uplink bandwidth is limited, add `-BuildOnVm`. This sends committed backend source instead of repeated fat JAR libraries, and the target installs JDK 17/Maven and builds the same reactor. Kafka and Alloy downloads are verified on the target. Local ignored credentials are exported separately. Commit desired backend source changes before using this mode.

The web UI is deployed separately to Cloudflare Workers by GitHub Actions. Set `AZURE_PAGES_ORIGIN` to the Worker production origin so the backend package applies the matching CORS allowlist. After changing the backend host, update `AZURE_BACKEND_HOST` and redeploy the Worker through CI/CD.

For Azure Students, check the subscription is Enabled and its spending limit remains On before creating resources. A VM can consume student credit while idle. Stop with Deallocate when it is not needed; disks/network may continue to consume credit. No paid upgrade is performed by these scripts.
