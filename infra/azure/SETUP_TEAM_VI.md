# Hướng dẫn nhóm: Cloudflare Pages + Java trên Azure VM

Đối chiếu với repo và tài liệu nhà cung cấp ngày 06/10/2026.

## 1. Hiểu đúng hệ thống

```text
Repo chính / main
  ├─ Cloudflare Git integration → build Flutter → website Pages
  └─ GitHub Actions CI → Publish backend release → Azure Blob private
                                                     ↓
                                      VM đọc khi bật / mỗi 2 phút
                                                     ↓
                                        build Java → systemd → HTTPS
```

Cloudflare chạy giao diện. VM chạy Java/Kafka/Caddy trực tiếp, không Docker. GitHub phát hành gói; nó không bật VM. Nếu VM tắt, giao diện vẫn tải nhưng tính năng dùng API sẽ không hoạt động.

Cấu hình hiện có gắn với repo test `Jandz01/DoAn1`. Repo chính không tự nhận các GitHub variables, Cloudflare connection hoặc OIDC trust từ repo test.

**Cách chuyển sang repo chính mà dùng lại VM:** thực hiện các mục 2, 3, 4, 5 và 6. VM đã có runtime/updater, không cần cài lại. Nếu dựng máy mới, làm thêm mục 7 trước khi kiểm tra cuối.

Người thao tác cần quyền quản trị repo GitHub, quyền cấu hình Cloudflare và quyền Azure để tạo identity/cấp role. Có thể chia việc: chủ Azure làm phần Azure, người quản trị GitHub làm phần GitHub.

## 2. Chuẩn bị repo chính

Tích hợp bản refactor đang chạy và các file sau vào repo chính, giữ nguyên các thay đổi tính năng của nhóm:

- `pom.xml`: giữ compiler plugin 3.13.0 với parameter metadata và Surefire 3.2.5.
- Source trong `common`, `services`, `infra/api-gateway`, `infra/config-server`, `infra/eureka-server`, `config-repo`, `frontend` và các cloud-free profiles.
- `.github/workflows/ci-cd.yml`, `.github/workflows/backend-release.yml`.
- Toàn bộ `infra/azure`, `infra/cloudflare`, `infra/no-docker` và `.gitignore`.

Không chuyển cả thư mục `.runtime/tools`, `.runtime/state`, `.runtime/packages`, build output hoặc SSH key sang repo nhóm. Credential ứng dụng được xử lý riêng bên dưới.

Hướng dẫn này dùng branch `main`. Nếu nhóm dùng `master`/branch khác, cần đổi đồng bộ CI triggers, backend release conditions, hai lệnh đọc `commits/main`, OIDC subject và Cloudflare production branch.

### Credential backend

Workflow đọc credential từ GitHub Secret `BACKEND_ENV_PS1`, tạo `.runtime/env.ps1` tạm trên runner và xóa sau khi đóng gói. Không commit env thật, kể cả repo private.

1. Thu hồi/đổi các credential từng xuất hiện trong Git trước khi dùng lại: Supabase, MongoDB, Redis/Valkey, S3, JWT và Grafana. Revert hoặc xóa file không xóa credential khỏi lịch sử.
2. Cập nhật credential mới trong file env local (được ignore).
3. Trong repo nhóm → **Settings → Secrets and variables → Actions → Secrets**, tạo `BACKEND_ENV_PS1` với nội dung env mới. Không dán credential vào chat hoặc pull request.
4. Cấu hình Azure/Cloudflare theo các mục sau. Chỉ bật `AZURE_BACKEND_DEPLOY_ENABLED` sau khi Secret và OIDC của repo nhóm đã hoàn tất.

GitHub variables Azure không thay thế credential ứng dụng. Không đặt password/token backend vào Cloudflare variables vì đây là build giao diện.

## 3. Cloudflare: liên kết repo chính

Thực hiện trên [Cloudflare Dashboard](https://dash.cloudflare.com/):

1. **Workers & Pages → Create application → Pages → Connect to Git / Import an existing Git repository**. Tên nút có thể khác nhẹ giữa giao diện.
2. Đăng nhập GitHub, cấp ứng dụng Cloudflare quyền truy cập **repo chính**. Nếu repo thuộc organization, quản trị organization có thể phải duyệt cài đặt.
3. Chọn repo chính, tạo một Pages project cho nhóm; ghi lại URL production mà Cloudflare cấp. Không dùng URL preview theo commit.
4. Cấu hình build:

| Trường | Giá trị |
|---|---|
| Production branch | `main` |
| Framework preset | `None` |
| Root directory | Gốc repo, để trống; không chọn `frontend` |
| Build command | `bash infra/cloudflare/build-pages.sh` |
| Build output directory | `frontend/build/web` |

5. **Project → Settings → Environment variables / Variables and Secrets**, chọn **Production**:

| Biến | Giá trị |
|---|---|
| `API_BASE_URL` | `https://HOST_BACKEND` — không có `/api`, port hoặc path |
| `BACKEND_PENDING` | `true` trong lúc chưa kiểm tra backend; chuyển `false` sau khi backend sẵn sàng |
| `NODE_VERSION` | `24` |

Script trong repo tự tải Flutter 3.47.5 và build. Không cần cài Flutter bằng tay trong Dashboard.

6. **Settings → Builds → Branch control**: bật automatic production deployments; production branch `main`. Nếu chỉ cần website chính, chọn preview branch **None**.
7. **Save and Deploy**; xem **Deployments → View build log**. Sau này lưu thay đổi biến build phải tạo deployment mới / retry deployment để giá trị đi vào Flutter.

Nếu repo không xuất hiện: GitHub account/organization **Settings → Applications → Installed GitHub Apps → Cloudflare → Configure**, cấp quyền repo chính rồi quay lại Cloudflare.

Tạo project Pages mới là quy trình dễ lặp lại để chuyển repo. Nếu cần giữ chính xác URL `doan1-8kk.pages.dev`, xử lý riêng connection/deployment của project cũ; không giả định việc cấp quyền GitHub cho repo khác tự đổi source của project đó.

[Nguồn: Git integration](https://developers.cloudflare.com/pages/get-started/git-integration/) và [build configuration](https://developers.cloudflare.com/pages/configuration/build-configuration/).

## 4. GitHub: nhập variables của backend

Repo chính → **Settings → Secrets and variables → Actions → Variables → New repository variable**.

| Tên | Khi dùng lại Azure/VM hiện tại | Khi tạo Azure mới: lấy ở đâu |
|---|---|---|
| `AZURE_BACKEND_DEPLOY_ENABLED` | `true` | `true` |
| `AZURE_BACKEND_CLIENT_ID` | `550a2f09-885f-4c8d-a273-a26a9d097a9c` | Managed Identities → identity của GitHub → Overview → Client ID |
| `AZURE_TENANT_ID` | `5ed8220f-e3c5-42c1-83f0-8843548a9b0d` | Microsoft Entra ID → Overview → Tenant ID |
| `AZURE_SUBSCRIPTION_ID` | `165c7def-d5c5-4d34-8648-cdd073c4381e` | Subscriptions → subscription dùng deploy → Subscription ID |
| `AZURE_RELEASE_STORAGE_ACCOUNT` | `doan1deploy165c7def` | Tên Storage account ở mục 5; không phải URL hoặc access key |
| `AZURE_BACKEND_HOST` | `doan1-165c7def.centralindia.cloudapp.azure.com` | DNS hostname của VM, không có `https://` hoặc path |
| `AZURE_PAGES_ORIGIN` | URL HTTPS production của Pages nhóm ở mục 3 | Ví dụ `https://ten-nhom.pages.dev`, không có path hoặc dấu `/` cuối |

Các ID trên không phải password; chỉ dùng cột giữa nếu dùng đúng tài nguyên Azure hiện tại và có quyền của chủ tài nguyên. VM trong tài khoản Azure khác cần ID/tên tài nguyên của tài khoản đó.

Với cách Pages Git integration ở mục 3, để `CLOUDFLARE_DEPLOY_ENABLED=false` hoặc không tạo biến này. Không cần `CLOUDFLARE_API_TOKEN` cho cách đó. Job Cloudflare Direct Upload trong CI là lựa chọn riêng.

Repo → **Actions**, bật workflows nếu đây là fork đang bị tắt Actions. Giữ tên workflow `CI/CD` vì `backend-release.yml` chờ workflow có tên này.

## 5. Azure: cho repo chính quyền phát hành gói

### 5A. Dùng lại Azure hiện tại

Trước khi chuyển publisher, tại **repo test** → Settings → Secrets and variables → Actions → Variables, đổi `AZURE_BACKEND_DEPLOY_ENABLED=false`; chờ workflow publish đang chạy xong. Như vậy hai repo không cùng ghi `latest.json` vào một container.

[Azure Portal](https://portal.azure.com/) → tìm **Managed Identities** → chọn **doan1-github-deploy** → **Settings → Federated credentials → Add Credential**.

Tạo credential riêng cho repo chính, ví dụ tên `github-main-team`. Chọn **Other issuer** để nhập chính xác:

| Trường | Giá trị |
|---|---|
| Issuer | `https://token.actions.githubusercontent.com` |
| Audience | `api://AzureADTokenExchange` |
| Subject identifier | Subject của **repo chính**, branch `main` |

**Subject phải khớp tuyệt đối**, kể cả chữ hoa/thường và ID. GitHub hiện có hai dạng:

```text
repo:OWNER/REPO:ref:refs/heads/main
repo:OWNER@OWNER_ID/REPO@REPO_ID:ref:refs/heads/main
```

Repo tạo/đổi tên/chuyển chủ từ 15/07/2026 dùng dạng có ID theo chính sách GitHub. Không lấy subject/ID của repo test để điền repo chính.

Cách lấy chắc chắn: sau CI đầu tiên, mở repo chính **Actions → Publish backend release → publish → Run azure/login@v2 → Federated token details → subject claim**. Chỉ lấy chuỗi `repo:...`, không lấy token. Nếu login lần đầu báo `AADSTS700213`, nhập subject đúng ở Azure, đợi cập nhật rồi GitHub **Re-run failed jobs**. Có thể lấy IDs bằng GitHub CLI đã đăng nhập: `gh api repos/OWNER/REPO --jq '{owner_id: .owner.id, repo_id: .id}'`; format subject vẫn cần đối chiếu dạng token repo đang dùng.

Identity hiện tại đã có role ghi container; VM hiện tại đã có role đọc, nên không cần cấp lại hay cài lại updater. Sau khi repo chính publish thành công, có thể gỡ federated credential chỉ dành cho repo test để kết thúc quyền publisher cũ.

[Nguồn: giao diện federated credential](https://learn.microsoft.com/en-us/entra/workload-id/workload-identity-federation-create-trust-user-assigned-managed-identity) và [immutable subject](https://learn.microsoft.com/en-us/entra/workload-id/workload-identities-github-immutable-subjects).

### 5B. Nếu tạo tài nguyên Azure mới

1. **Storage accounts → Create**: chọn đúng subscription/resource group, Standard, LRS, StorageV2; dùng tên duy nhất.
2. **Storage account → Configuration**: HTTPS only, TLS tối thiểu 1.2, tắt anonymous Blob access và storage account key access.
3. Cấp người thao tác **Storage Blob Data Contributor** ở Storage account IAM để tạo container bằng Microsoft Entra authentication. Role quản lý `Owner` không tự thay thế quyền đọc/ghi Blob.
4. **Data storage → Containers → + Container**, tên **`releases`**, anonymous access **Private**.
5. **Managed Identities → Create**: tạo user-assigned identity, ví dụ `ten-nhom-github-deploy`. Đây là identity của workflow; không phải SSH user hoặc identity đọc gói của VM. Tạo federated credential như mục 5A cho repo chính; copy Client ID vào GitHub variable.
6. **VM → Identity → System assigned → On → Save**.
7. **Storage account → Containers → releases → Access Control (IAM) → Add role assignment**:
   - GitHub user-assigned identity: **Storage Blob Data Contributor**.
   - VM system-assigned identity: **Storage Blob Data Reader**.
8. Đợi quyền có hiệu lực. Phạm vi cấp là container `releases`; workflow này không cần SSH key hoặc quyền quản lý VM.

[Nguồn: tạo Storage account](https://learn.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal) và [cấp quyền Blob](https://learn.microsoft.com/en-us/azure/storage/blobs/assign-azure-role-data-access).

## 6. Kiểm tra và chuyển website sang backend

1. Push một commit vào `main` repo chính.
2. Repo chính → **Actions**: `CI/CD` phải xanh, rồi `Publish backend release` phải xanh. Cloudflare → **Deployments** phải thấy commit của repo chính.
3. Nếu dùng VM hiện tại: Azure → **Virtual machines → doan1-backend → Overview → Start**. Updater đã cài; không cần clone Git vào VM hay chạy `git pull` bằng tay.
4. Chờ build/start hoàn tất. VM Running chưa đồng nghĩa Java đã sẵn sàng. Khi Pages URL đổi, release mới sẽ cập nhật CORS theo `AZURE_PAGES_ORIGIN`; phải dùng đúng URL này để test.
5. SSH vào VM chỉ khi cần xem trạng thái:

```bash
sudo systemctl status doan-auto-update.timer --no-pager
sudo journalctl -u doan-auto-update -n 30 --no-pager
sudo cat /var/lib/doan-deploy/state.json
```

State phải `healthy`; `revision` theo release mới nhất. `active_revision` có thể cũ hơn nếu commit chỉ đổi docs/UI và backend digest không đổi. Nếu cần log lỗi build, đọc `/var/log/doan-deploy/<commit>.log` trên VM; không chia sẻ nguyên log/credential ra công khai.

6. Kiểm HTTPS, đăng nhập bằng tài khoản test, feed, upload và chat giữa hai tài khoản. Các kiểm tra health không thay thế kiểm tra tính năng này.
7. Cloudflare → project → Settings → production variables: `API_BASE_URL=https://HOST_BACKEND`, `BACKEND_PENDING=false`; **redeploy**.
8. Kết thúc test: Azure VM → **Overview → Stop**, đợi **Stopped (deallocated)**. Không dùng Delete. VM tắt vẫn nhận release mới trong Blob; áp dụng khi bạn bật lại.

## 7. Chỉ làm mục này nếu dựng VM mới

### 7A. Tạo máy

Azure Portal → **Virtual machines → Create → Azure virtual machine**:

- Subscription: tài khoản của người vận hành, ví dụ Azure for Students.
- Resource group: tạo một nhóm riêng cho đồ án.
- Image: **Ubuntu Server 24.04 LTS, x64**.
- Cấu hình đã kiểm tra cho toàn bộ backend hiện tại: **2 vCPU, 16 GB RAM**, máy cũ là `Standard_E2as_v4`. Kiểm tra giá/quota ở màn hình trước khi tạo; không chọn máy 1 GB chỉ vì thấy nhãn free.
- Authentication: **SSH public key**; username ví dụ `doanadmin`; lưu private key vừa tạo trên máy người quản trị.
- Public IP: Standard, static; mở 80/443 cho HTTPS, SSH 22 chỉ IP người quản trị.

Sau tạo: **VM → Overview → Public IP → Configuration → DNS name label → Save**. Ghi hostname Azure cấp. Nhập hostname đó vào `AZURE_BACKEND_HOST`, và bản HTTPS vào Cloudflare `API_BASE_URL`.

**VM → Networking / Network settings → NSG inbound rules**: chỉ cần TCP 80, 443 public; TCP 22 giới hạn IP quản trị. Không mở các port Java, Eureka, Config Server hoặc Kafka ra Internet.

MongoDB Atlas → **Database & Network Access → IP Access List → Add IP Address**: thêm public IP VM dạng `/32`, đợi Active. Trong Supabase SQL Editor, chạy `infra/no-docker/config/supabase/init-schemas.sql` trước lần khởi động native runtime đầu tiên. Điền đúng credential hiện dùng cho Supabase/Mongo/Valkey/S3.

[Nguồn: tạo Linux VM](https://learn.microsoft.com/en-us/azure/virtual-machines/linux/quick-create-portal) và [DNS hostname VM](https://learn.microsoft.com/en-us/azure/virtual-machines/create-fqdn).

### 7B. Cài runtime lần đầu

Máy Windows của người quản trị: cần Git, PowerShell, OpenSSH/scp và `tar`; chạy lệnh từ gốc checkout repo chính. Chuẩn bị `.runtime/env.ps1` thật trên máy quản trị kể cả khi GitHub dùng Secret. Nếu chưa có, tạo từ `infra/no-docker/env.example.ps1` rồi điền credential; không ghi đè env đã có.

Nếu `GRAFANA_ENABLED=true`, `Build-LinuxPackage.ps1 -BuildOnVm` cần file ZIP Alloy Linux ở `.runtime/tools/alloy-linux/alloy-linux-amd64.zip` để lấy checksum. Bản scripts hiện dùng [Alloy 1.18.0](https://github.com/grafana/alloy/releases/tag/v1.18.0). Chạy từ PowerShell gốc repo:

```powershell
New-Item -ItemType Directory -Force .runtime/tools/alloy-linux | Out-Null
$release = Invoke-RestMethod 'https://api.github.com/repos/grafana/alloy/releases/tags/v1.18.0'
$asset = $release.assets | Where-Object name -eq 'alloy-linux-amd64.zip'
if (-not $asset.digest) { throw 'Release asset checksum is required' }
Invoke-WebRequest $asset.browser_download_url -OutFile .runtime/tools/alloy-linux/alloy-linux-amd64.zip
$hash = (Get-FileHash .runtime/tools/alloy-linux/alloy-linux-amd64.zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne $asset.digest.Replace('sha256:', '')) { throw 'Alloy checksum mismatch' }
```

Có thể dùng `GRAFANA_ENABLED=false` nếu không cần monitoring. Nếu chọn cách này, muốn bật Grafana về sau cần cài thêm Alloy Linux và systemd unit trước khi bật biến; updater không tự cài binary/unit Alloy còn thiếu.

Trước lần scp/SSH đầu tiên, đối chiếu host key với máy chủ Azure: **VM → Run command → RunShellScript**, chạy `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` và so fingerprint với prompt SSH. Không bỏ kiểm tra bằng tùy chọn `StrictHostKeyChecking=no`.

```powershell
# HOST chỉ là hostname; PagesOrigin là URL HTTPS production của nhóm.
./infra/no-docker/scripts/Build-LinuxPackage.ps1 -BuildOnVm `
  -BackendHost 'HOST_BACKEND' `
  -PagesOrigin 'https://TEN_PROJECT.pages.dev'

# KEY_PATH là đường dẫn private key SSH; VM_IP là IP thật.
scp -i 'KEY_PATH' .runtime/packages/azure-runtime.tar.gz doanadmin@VM_IP:/home/doanadmin/
ssh -i 'KEY_PATH' doanadmin@VM_IP
```

Trong SSH shell Ubuntu:

```bash
sudo mkdir -p /opt/doan
sudo tar -xzf /home/doanadmin/azure-runtime.tar.gz -C /opt/doan
sudo bash /opt/doan/bin/install.sh
sudo systemctl status doan-start --no-pager
```

Installer cài Java 17/Maven/Caddy, tải Kafka và optional Alloy với checksum, build JAR và tạo systemd units. Gói chứa backend env; giữ ở máy quản trị/VM đúng mục đích. Chờ runtime đầu tiên hoạt động trước bước tiếp theo.

### 7C. Cài bộ tự cập nhật

Hoàn thành storage/identity/role ở mục 5B và bảo đảm `Publish backend release` đã phát hành `latest.json` trước khi chạy installer updater.

Từ PowerShell gốc repo:

```powershell
ssh -i 'KEY_PATH' doanadmin@VM_IP 'mkdir -p /home/doanadmin/auto-update-install'
scp -i 'KEY_PATH' infra/azure/deploy_agent.py infra/azure/install-auto-update.sh doanadmin@VM_IP:/home/doanadmin/auto-update-install/
ssh -i 'KEY_PATH' doanadmin@VM_IP
```

Trên Ubuntu:

```bash
# Chuyển CRLF nếu checkout Windows đã đổi line endings của shell script.
sed -i 's/\r$//' /home/doanadmin/auto-update-install/install-auto-update.sh
sudo bash /home/doanadmin/auto-update-install/install-auto-update.sh TEN_STORAGE_ACCOUNT
sudo systemctl is-enabled doan-auto-update.timer
sudo cat /var/lib/doan-deploy/state.json
```

`TEN_STORAGE_ACCOUNT` phải đúng account GitHub đang publish vào. Updater dùng VM identity, không dùng token Azure CLI của laptop. `install-auto-update.sh` yêu cầu native runtime có sẵn; chỉ clone repo vào VM chưa đáp ứng điều kiện này.

Sau đó quay lại kiểm tra mục 6. Khi nghỉ test/demo, Stop/Deallocate VM. Azure for Students dùng credit; VM này không phải hosting miễn phí vĩnh viễn. Deallocate ngừng compute, disk/IP/storage vẫn có thể có phí: [VM states and billing](https://learn.microsoft.com/en-us/azure/virtual-machines/states-billing).

## 8. Những lỗi thường gặp

| Hiện tượng | Kiểm tra ở đâu |
|---|---|
| Repo không xuất hiện trên Cloudflare | GitHub App Cloudflare chưa có quyền repo/organization |
| Pages báo thiếu `API_BASE_URL` | Production variables; root repo/build command đúng |
| Java báo thiếu tên String parameter | Repo chính thiếu compiler fix trong root POM; kiểm bản deployed |
| Không chạy Publish backend release | Actions disabled, enabled variable chưa true, CI chưa success hoặc tên workflow CI sai |
| Azure login `AADSTS700213` | Subject OIDC chưa đúng repo/branch/ID/case |
| Upload Blob 403 | GitHub identity thiếu Data Contributor đúng container; quyền chưa có hiệu lực |
| VM tải Blob 403 | VM System assigned identity/Blob Data Reader |
| Updater không chạy trên máy mới | Chưa cài native runtime + updater; GitHub chỉ publish gói |
| Mongo timeout | IP VM chưa Active trong Atlas; URI/user/password/DNS |
| Browser CORS error | `AZURE_PAGES_ORIGIN` khác URL production đang mở; cần release mới áp dụng |
| API 502 lúc bật máy | Java chưa start xong; xem doan-start và health trước |
| VM vẫn trừ credit dù không ai dùng | VM Running; Stop/Deallocate khi không cần |

## 9. Công việc hằng ngày của nhóm

Sửa code → PR/merge `main` → xem CI và Pages deployment → xem Publish backend release. Nếu VM tắt, bật để test/demo; nó tự deploy release mới nhất đã đạt CI. Nếu VM đang chạy, updater kiểm tra mỗi 2 phút. CI/build/health lỗi thì xử lý lỗi đó trong commit mới; không cần tự thay JAR bằng tay.

Service mới cần Maven module + Spring Boot plugin, port riêng, cloud profile/credential và gateway route. Xem `AUTO_DEPLOY_VI.md` cho override/migration. Chỉ thêm code service chưa cấu hình database/key không đủ để tự chạy.
