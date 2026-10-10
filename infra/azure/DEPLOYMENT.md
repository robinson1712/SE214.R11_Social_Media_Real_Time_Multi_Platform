# Azure Students + Cloudflare Pages

Current owner-requested state: VM deallocated on 06/10/2026 to save compute credit. See [Vietnamese start/stop guide](VM_CONTROL_VI.md). The Maven parameter-metadata fix and automatic backend updater have been deployed and verified; see [automatic deployment](AUTO_DEPLOY_VI.md) and its [verification report](AUTO_DEPLOY_VERIFICATION_2026-10-06.md).

## Resources created on 06/10/2026

| Item | Value |
|---|---|
| Subscription | Azure for Students, Enabled; spending limit On |
| Resource group | `doan1-demo-rg` |
| VM | `doan1-backend`, Ubuntu 24.04 x64, Trusted Launch |
| Size / region | `Standard_E2as_v4`, 2 vCPU / 16 GB RAM, Central India |
| Public IP | `20.219.159.34` |
| Backend origin | `https://doan1-165c7def.centralindia.cloudapp.azure.com` |
| Frontend | `https://doan1-8kk.pages.dev` |

The backend runs Java 17 JARs, native Kafka, Grafana Alloy and Caddy under systemd. Docker is not installed. Java/Kafka/Eureka/Config Server listen on loopback. NSG opens TCP 80/443 for HTTPS and allows SSH only from the administrator's current IP. Caddy proxies API/WebSocket paths and returns 404 for other backend paths; actuator/internal/docs endpoints are not exposed by this proxy.

MongoDB Atlas was updated by the owner to allow the VM IP. A probe executed on the VM verified all ten Supabase schemas, all four MongoDB databases, Valkey AUTH/PING and Storage authenticated HeadBucket. The Maven reactor compiled successfully on target Java 17. Cloud runtime health and final frontend integration results are recorded in the deployment report after verification; source/unit evidence is separate from real login, upload and chat acceptance.

All 19 Java health endpoints were UP, backend HTTPS/auth validation/CORS checks passed, and the Pages deployment using the real API origin completed. See [verification report](VERIFICATION_REPORT_2026-10-06.md). Large Windows CI bundles are optional manual artifacts, retained one day; they are not needed to run the Azure VM.

## Start and stop

From this workstation, Azure CLI credentials and SSH material stay under ignored `.runtime/state`. With the CLI signed in:

```powershell
./infra/azure/manage-vm.ps1 -Action Status
./infra/azure/manage-vm.ps1 -Action Start
./infra/azure/manage-vm.ps1 -Action Stop
```

`Stop` performs **deallocate**. This stops compute billing; disks and public IP can still consume credit. `Start` brings up the VM, then the boot unit starts Kafka and the Java services sequentially. The app needs these services ready before login/chat works. Cloudflare static frontend remains available while the VM is off, but backend features are unavailable.

No automatic shutdown schedule or paid subscription upgrade has been configured. `Restart=on-failure` retries the boot orchestration if a dependency is temporarily unavailable. Service logs are rotated to bound disk usage. Private SSH key material and exported per-service credentials are not frontend assets.

## Credit estimate

The Microsoft Retail Prices API returned Linux Consumption price **$0.08/hour** for E2as v4 in Central India on 06/10/2026. Compute alone is about $1.92/day or $58.40 per 730-hour month when continuously running. Disk, IP, storage operations and traffic are additional. This is not a promise that $100 can support six months of 24/7 operation.

Student spending limit remains On. Check actual usage in Azure Education Hub/Cost Management; keeping the VM deallocated outside test/demo time can extend credit. Do not upgrade to Pay-As-You-Go when the objective is to stay within the student credit.

Sources: [Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices), [VM billing states](https://learn.microsoft.com/en-us/azure/virtual-machines/states-billing), [Azure for Students FAQ](https://learn.microsoft.com/en-us/azure/education-hub/faq).

## Rebuild / redeploy

`infra/no-docker/scripts/Build-LinuxPackage.ps1 -BuildOnVm` packages committed backend source and exported current credentials. It avoids uploading repeated fat-JAR dependencies through a slow uplink. The target installer builds Java, verifies native Kafka/Alloy downloads and writes service units. Use the correct backend hostname and Pages origin; keep the resulting package private.

For normal team changes, push or merge into `main`. Successful CI publishes a private source release automatically. The installed VM updater applies it on next boot or on its two-minute timer while running. GitHub does not start the VM. Backend changes and new executable modules use the discovery/configuration rules in `AUTO_DEPLOY_VI.md`.

When the backend origin changes, update Cloudflare Pages production `API_BASE_URL` and deploy again. `BACKEND_PENDING=false` is appropriate only after the backend HTTPS routes and CORS have been verified. Keep optional GitHub Direct Upload disabled when using the existing Pages Git integration.
