# Operations

## Local bootstrap

1. Chuẩn bị `.env` từ `.env.example`.
2. Bật Docker daemon.
3. Chạy `docker compose up -d --build`.

One-shot init hiện tại:

1. `core-api-init`
2. `auth-service-init`
3. `notification-service-init`
4. `finance-service-init`
5. `academic-service-init`
6. `engagement-service-init`
7. `people-service-init`
8. `analytics-service-init`
9. runtime services

## Verify workflows

- Fast local E2E: `node scripts/run-fast-e2e.mjs`
- Edge E2E qua `nginx`: `node scripts/run-edge-e2e.mjs`
- Shared Compose E2E (repeatable session setup): from `frontend`, set
  `E2E_EXTERNAL_STACK=1` and
  `E2E_AUTH_LOGIN_URL=http://127.0.0.1:4007/api/v1`, then run
  `npx playwright test`. The direct auth URL is only a seeded-session harness
  path; edge auth, CSRF, and gateway security tests still use nginx. This does
  not replace an isolated database run because the finance checkout fixture is
  mutating by design.
- Production-like image smoke: `node scripts/run-image-smoke.mjs`
- Local security sweep: `node scripts/run-security-local.mjs`
- Production compose preflight: `node scripts/check-production-compose-config.mjs`
- Kubernetes preflight: `node scripts/run-k8s-preflight.mjs`
- Kubernetes local smoke: `node scripts/run-k8s-local-smoke.mjs`
- Kubernetes local deploy giữ nguyên resources: `node scripts/run-k8s-local-deploy.mjs`
- Kubernetes local edge helper: `node scripts/run-k8s-local-edge.mjs`
- Kubernetes local edge stop: `node scripts/stop-k8s-local-edge.mjs`
- Kubernetes local destroy: `node scripts/run-k8s-local-destroy.mjs`
- Cloudflare local tunnel: `node scripts/run-cloudflare-tunnel-local.mjs`
- Cloudflare local tunnel stop: `node scripts/stop-cloudflare-tunnel-local.mjs`
- Runtime container inventory: `node scripts/run-container-inventory.mjs`

## Runtime container inventory

Use the read-only inventory when the local machine has many containers from
CampusCore, monitoring, one-shot init jobs, and older projects:

```bash
node scripts/run-container-inventory.mjs
```

The script does not stop, remove, or mutate Docker containers or Kubernetes
resources. It reports:

- Docker container name, image, health, restart count, exposed ports, and recent
  error-log signal summary.
- Kubernetes pods, deployments, services, image tags, readiness, restart count,
  and recent error-log signal summary for the `campuscore` namespace.
- A clear classification for each item:
  - `healthy`: long-running CampusCore runtime or Kubernetes workload is ready.
  - `expected exited`: completed init/bootstrap jobs or transient probe
    containers that are not part of the runtime path.
  - `needs attention`: CampusCore runtime item with unhealthy status, unexpected
    exit, or restart history. Recent log signals are also printed beside healthy
    items so operators can decide whether they are normal component noise or a
    follow-up issue.
  - `external residue`: containers from other projects or old experiments. The
    audit records them but never removes them.

`campuscore-mailhog` is local-only developer tooling and may appear as running
without a Docker healthcheck. That is informational, not a production runtime
failure.

For CI-like behavior, set strict mode:

```bash
CONTAINER_INVENTORY_STRICT=1 node scripts/run-container-inventory.mjs
```

Strict mode exits non-zero when a CampusCore runtime item lands in
`needs attention`. It still ignores external residue.

## Health model

- Public liveness: `GET /health` qua `core-api`
- Internal readiness: `GET /api/v1/health/readiness`
- Service internal routes không public qua `nginx`

## Required runtime services

- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- `core-api`
- `notification-service`
- `auth-service`
- `finance-service`
- `academic-service`
- `engagement-service`
- `people-service`
- `analytics-service`
- `frontend`
- `nginx`

## Operational defaults

- `DOCKERHUB_USERNAME` là secret bắt buộc để publish Docker Hub.
- `DOCKERHUB_NAMESPACE` chỉ cần set khi namespace khác username.
- `DOCKERHUB_TOKEN` là secret đăng nhập ưu tiên cho Docker Hub publish từ CI/CD.
- `latest` chỉ cập nhật khi có semver release.

## Kubernetes deployment target

- Repo hiện có Kustomize manifests tại `k8s/base` và `k8s/bootstrap`, cùng overlay local-first tại `k8s/overlays/docker-desktop`.
- Topology K8s giữ nguyên boundary runtime hiện tại: `core-api`, `auth-service`, `notification-service`, `finance-service`, `academic-service`, `engagement-service`, `people-service`, `analytics-service`, `frontend`, `nginx`, cùng PostgreSQL, Redis, RabbitMQ, MinIO.
- Với Docker Desktop, đường chuẩn là:
  - `node scripts/run-k8s-preflight.mjs`
  - `node scripts/run-k8s-local-smoke.mjs`
- Nếu muốn giữ stack chạy để Docker Desktop UI nhìn thấy tài nguyên:
  - `node scripts/run-k8s-local-deploy.mjs`
  - sau đó đổi namespace từ `default` sang `campuscore` trong Docker Desktop Kubernetes UI
  - cách dùng khuyến nghị để mở edge local là `node scripts/run-k8s-local-edge.mjs`
  - helper này sẽ giữ local listener ổn định ở `http://127.0.0.1:8080`, đợi đủ `/health`, `/login`, `/api/docs`, và deny `/api/v1/internal/*` trước khi báo ready
  - helper tự quản lý `kubectl port-forward`, tự restart khi listener rơi bất ngờ, và ghi state/log vào `frontend/test-results/k8s-local-edge*`
  - các route contract như `/login`, `/api/docs`, và deny `/api/v1/internal/*` vẫn được verify trong `run-k8s-local-smoke.mjs` và `run-k8s-local-deploy.mjs`
  - nếu cần fallback thủ công, vẫn có thể dùng `kubectl -n campuscore port-forward service/campuscore-nginx 8080:80`, nhưng đây chỉ là đường debug
  - log `Handling connection for 8080` hoặc `error copying from local connection to remote stream ... wsarecv ...` từ raw `kubectl port-forward` thường chỉ là client-disconnect noise nếu `http://127.0.0.1:8080/health` vẫn lên
  - khi cần dọn, chạy `node scripts/run-k8s-local-destroy.mjs`
  - nếu chỉ muốn dừng listener local mà giữ cluster, chạy `node scripts/stop-k8s-local-edge.mjs`
  - nếu chỉ muốn reconcile lại runtime trên namespace đang tồn tại, dùng `K8S_REUSE_NAMESPACE=1 node scripts/run-k8s-local-deploy.mjs`
  - bootstrap jobs chỉ được replay khi chủ động set `K8S_FORCE_BOOTSTRAP_REPLAY=1`
- Overlay Docker Desktop bật Swagger local, tắt secure cookie flag cho HTTP local, và dùng `ClusterIP` + port-forward cho `campuscore-nginx`.
- Repo cũng đã có `k8s/overlays/staging-generic` và `k8s/overlays/prod-generic` làm khung cloud-agnostic cho staging/prod.
- Nếu cluster dùng `ExternalSecret` + `cert-manager`, có thể bắt đầu từ `k8s/overlays/staging-operator` hoặc `k8s/overlays/prod-operator` để thay static secret placeholder bằng operator-managed resources.
- Nếu muốn chuẩn bị overlay riêng cho staging/prod thật, copy từ `k8s/templates/private-operator/staging` hoặc `k8s/templates/private-operator/prod` ra private repo/overlay rồi điền hostname, TLS secret, ingress annotations, `ClusterSecretStore`, `ClusterIssuer`, và remote secret key thật.
- Render private template trước khi apply:
  - `kubectl kustomize k8s/templates/private-operator/staging`
  - `kubectl kustomize k8s/templates/private-operator/staging/bootstrap`
  - `kubectl kustomize k8s/templates/private-operator/prod`
  - `kubectl kustomize k8s/templates/private-operator/prod/bootstrap`
- Cloudflare nếu dùng sau này chỉ đứng trước ingress; checklist domain/DNS/TLS nằm tại `docs/CLOUDFLARE.md`.
- Checklist ingress/TLS/secrets cho generic overlays và operator overlays nằm tại `docs/K8S_HANDOFF.md`.
- Nếu chưa có IP public/cloud Kubernetes thật, có thể expose local Docker Desktop Kubernetes qua Cloudflare Tunnel bằng `node scripts/run-cloudflare-tunnel-local.mjs`. Với Docker connector, Public Hostname service URL trong Cloudflare là `http://host.docker.internal:8080`.
- Bootstrap schema/migration vẫn là bước operator-managed riêng, giống policy production compose hiện tại.
