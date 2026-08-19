# CampusCore Kubernetes

CampusCore hiện có bộ manifests Kustomize cho canonical topology 9 image đang
chạy ở Docker Compose. Java thesis có một overlay pilot riêng để kiểm tra
production-shaped routing mà không làm thay đổi release baseline:

- `core-api`
- `auth-service`
- `notification-service`
- `finance-service`
- `academic-service`
- `engagement-service`
- `people-service`
- `analytics-service`
- `frontend`
- `nginx`
- PostgreSQL, Redis, RabbitMQ, MinIO

`k8s/overlays/thesis-pilot` thêm `thesis-service` Java 21 trong namespace cô
lập `campuscore-thesis-pilot`. Overlay này không phải public release, không
được thêm vào danh sách GHCR images, và không được hiểu là production cutover.

## Mục tiêu

- Giữ nguyên boundary runtime đang có trong repo.
- Dùng public GHCR images của release hiện tại làm mặc định triển khai.
- Không tự động chạy migration trong app runtime; bootstrap schema vẫn là bước operator-managed giống production compose.

## Base và overlay

- `k8s/base`: canonical production-like runtime base cho topology hiện tại.
- `k8s/overlays/thesis-pilot`: candidate Java boundary với route nginx riêng,
  readiness/secret contract và image local-only; dùng để kiểm tra parity,
  migration, rollback trước khi mở staging/prod.
- `k8s/bootstrap`: one-shot init jobs theo đúng thứ tự bootstrap schema/data.
- `k8s/overlays/docker-desktop`: local-first overlay cho Docker Desktop Kubernetes.

Overlay Docker Desktop giữ nguyên boundary service nhưng vá đúng các điểm chỉ nên đổi ở local:

- `campuscore-nginx` dùng `ClusterIP` để port-forward ổn định.
- `SWAGGER_ENABLED=true` để local smoke kiểm được `/api/docs`.
- `COOKIE_SECURE=false` và `FRONTEND_URL=http://127.0.0.1:8080` để browser auth flow chạy được trên HTTP local.
- Secret dùng giá trị local-only, không phải production secret.

## Java thesis pilot

Pilot overlay dựng lại canonical runtime trong namespace riêng rồi thêm Java
thesis service và hai fragment nginx có phạm vi rõ:

- `k8s/base` giữ fragment rỗng, nên canonical nine-image runtime không resolve
  tới `thesis-service`.
- `k8s/overlays/thesis-pilot` thay fragment bằng upstream `thesis-service:4010`
  và chỉ route `/api/v1/thesis` vào Java.
- Deployment chờ Postgres và Redis, dùng `thesis` schema qua Flyway, secret-backed
  `JWT_SECRET`/`HEALTH_READINESS_KEY`, và readiness/liveness probes.
- Image mặc định là `campuscore-thesis-service:pilot-local`; operator phải
  build/load image vào cluster hoặc patch sang một digest đã publish trong
  private overlay. Repo chưa publish image này.

Render kiểm tra (không tự apply):

```bash
docker build -f java-services/thesis-service/Dockerfile -t campuscore-thesis-service:pilot-local java-services
kubectl kustomize k8s/overlays/thesis-pilot
```

Chỉ sau khi đã có image local và secret phù hợp mới apply:

```bash
kubectl apply -k k8s/overlays/thesis-pilot
```

Pilot không thay thế các overlay staging/prod. Trước khi mở route vào shared
environment vẫn phải có differential contract, schema/reconciliation/restore,
canary, rollback, metrics/logs/traces và exact-head Advisor/Kongming/Wukong
evidence.

## Dùng với Docker Desktop Kubernetes

1. Bật Kubernetes trong Docker Desktop.
2. Chạy preflight:

```bash
node scripts/run-k8s-preflight.mjs
```

3. Chạy smoke local đầy đủ:

```bash
node scripts/run-k8s-local-smoke.mjs
```

Script smoke sẽ:

- apply `k8s/overlays/docker-desktop`
- chờ infra sẵn sàng
- render `k8s/bootstrap` rồi apply các job theo đúng thứ tự
- chờ rollout runtime
- port-forward `campuscore-nginx`
- smoke `/health`, `/login`, `/api/docs`, và deny `/api/v1/internal/*`

Mặc định script sẽ cleanup namespace sau khi xong để tiết kiệm tài nguyên local. Nếu muốn giữ lại cluster state để tự kiểm tra tiếp:

```bash
K8S_KEEP_RESOURCES=1 node scripts/run-k8s-local-smoke.mjs
```

## Deploy giữ nguyên resources

Nếu bạn muốn Docker Desktop Kubernetes UI hiển thị đủ pods/deployments/services của CampusCore sau khi chạy xong, dùng script deploy riêng:

```bash
node scripts/run-k8s-local-deploy.mjs
```

Script này sẽ:

- apply `k8s/overlays/docker-desktop`
- chờ infra sẵn sàng
- chạy bootstrap jobs theo thứ tự chuẩn
- chờ rollout toàn bộ runtime
- chạy một lượt smoke ngắn qua port-forward
- **không xóa namespace `campuscore`**

Nếu bạn chỉ muốn reconcile lại runtime trên namespace đang có sẵn resources:

```bash
K8S_REUSE_NAMESPACE=1 node scripts/run-k8s-local-deploy.mjs
```

Chế độ này không replay bootstrap jobs. Nếu thật sự cần chạy lại init jobs trên namespace đang tồn tại, hãy chủ động thêm:

```bash
K8S_REUSE_NAMESPACE=1 K8S_FORCE_BOOTSTRAP_REPLAY=1 node scripts/run-k8s-local-deploy.mjs
```

Sau khi script deploy hoàn tất:

1. Trong Docker Desktop Kubernetes UI, đổi namespace từ `default` sang `campuscore`
2. Nếu muốn mở edge bằng browser hoặc IAB mà không cần nhớ lệnh `kubectl port-forward`, dùng helper:

```bash
node scripts/run-k8s-local-edge.mjs
```

Helper này sẽ:

- kiểm tra cluster và namespace `campuscore`
- mở supervisor nền quản lý `kubectl port-forward`
- tự restart listener nếu `kubectl` rơi bất ngờ nhưng cluster/runtime vẫn khỏe
- đợi đủ `/health`, `/login`, `/api/docs`, và deny `/api/v1/internal/*` trước khi báo ready
- ghi state file và log file để có thể dừng gọn bằng script đối ứng

Helper này là **đường local chính thức** cho browser/IAB. Log raw kiểu:

- `Handling connection for 8080`
- `error copying from local connection to remote stream ... wsarecv: An existing connection was forcibly closed by the remote host`

được coi là **benign client-disconnect noise** nếu `http://127.0.0.1:8080/health` vẫn lên bình thường.

Các bài contract check sâu hơn như `/login`, `/api/docs`, và deny `/api/v1/internal/*` vẫn được khóa ở `run-k8s-local-smoke.mjs` và `run-k8s-local-deploy.mjs`.

Khi muốn dừng helper:

```bash
node scripts/stop-k8s-local-edge.mjs
```

3. Nếu cần fallback thủ công, vẫn có thể mở edge bằng:

```bash
kubectl -n campuscore port-forward service/campuscore-nginx 8080:80
```

Lệnh này chỉ còn là **debug fallback**. Terminal sẽ giữ mở cho tới khi bạn nhấn `Ctrl+C`, và trên Windows bạn có thể thấy log `wsarecv` hoặc `error copying from local connection to remote stream` khi browser/IAB tự đóng socket. Nếu `/health` vẫn lên, đó không phải là dấu hiệu stack bị treo.

Rồi mở:

- `http://127.0.0.1:8080/health`
- `http://127.0.0.1:8080/login`
- `http://127.0.0.1:8080/api/docs`

Nếu cần dọn stack local này đi sau khi kiểm tra xong:

```bash
node scripts/run-k8s-local-destroy.mjs
```

Script destroy cũng sẽ dọn state của edge helper nếu đang tồn tại.

## Expose local qua Cloudflare Tunnel

Nếu chưa có IP public hoặc cloud Kubernetes thật, bạn có thể đưa Docker Desktop Kubernetes local ra internet bằng Cloudflare Tunnel:

```bash
node scripts/run-cloudflare-tunnel-local.mjs
```

Không có `CLOUDFLARE_TUNNEL_TOKEN` thì script tạo quick tunnel tạm thời `https://*.trycloudflare.com`. Nếu muốn dùng hostname thật trong Cloudflare, tạo named tunnel trong Cloudflare Zero Trust, copy token vào biến môi trường `CLOUDFLARE_TUNNEL_TOKEN`, rồi chạy lại script.

Vì máy Windows hiện dùng Docker connector mặc định, Public Hostname service URL trong Cloudflare nên là:

```text
http://host.docker.internal:8080
```

Nếu muốn dừng Docker connector:

```bash
node scripts/stop-cloudflare-tunnel-local.mjs
```

Chi tiết từng bước nằm tại [../docs/CLOUDFLARE.md](../docs/CLOUDFLARE.md).

## Manual flow nếu cần tự điều khiển

1. Render runtime:

```bash
kubectl kustomize k8s/overlays/docker-desktop
```

2. Apply runtime:

```bash
kubectl apply -k k8s/overlays/docker-desktop
```

3. Bootstrap vẫn chạy tách riêng để tránh app runtime tự đụng migration.

Thứ tự khuyến nghị của bootstrap:

1. `core-api-init`
2. `auth-service-init`
3. `notification-service-init`
4. `finance-service-init`
5. `academic-service-init`
6. `engagement-service-init`
7. `people-service-init`
8. `analytics-service-init`

Nếu cần chạy từng job thủ công, dùng manifest đã render từ:

```bash
kubectl kustomize k8s/bootstrap
```

thay vì apply raw file trực tiếp, để image tags luôn khớp release đang khóa trong Kustomize.

## Public entrypoint

- `campuscore-nginx` được expose bằng `Service` kiểu `LoadBalancer` trong `k8s/base`.
- Docker Desktop overlay đổi service này sang `ClusterIP` và dùng port-forward:

```bash
kubectl -n campuscore port-forward service/campuscore-nginx 8080:80
```

Với local Docker Desktop, cách dùng khuyến nghị là:

```bash
node scripts/run-k8s-local-edge.mjs
```

để browser/IAB luôn có local listener ổn định tại `http://127.0.0.1:8080`. State hiện hành của helper được ghi ở `frontend/test-results/k8s-local-edge-state.json`, còn log raw/classified được ghi trong `frontend/test-results/k8s-local-edge/`.

## Cloud-agnostic overlays

Repo đã có sẵn hai overlay generic cho pha vận hành kế tiếp:

- `k8s/overlays/staging-generic`
- `k8s/overlays/prod-generic`

Hai overlay này:

- giữ nguyên topology canonical 9 image và boundary hiện tại
- thêm `Ingress` chuẩn Kubernetes
- dùng hostname placeholder và TLS secret name placeholder
- không commit secret thật
- không hard-code `ingressClassName`, để cluster chọn default ingress class hoặc để operator vá bằng private overlay nếu cần

Repo cũng có hai overlay operator kế thừa trực tiếp từ generic overlays:

- `k8s/overlays/staging-operator`
- `k8s/overlays/prod-operator`

Hai overlay này vẫn giữ `Ingress` từ lớp generic, nhưng:

- xóa `Secret campuscore-secrets` placeholder khỏi public overlay
- thay nó bằng `ExternalSecret` để operator bind secret manager thật
- thêm `Certificate` để TLS secret được cấp bởi `cert-manager`
- vẫn không hard-code `ingressClassName`, để cluster giữ quyền chọn ingress class mặc định hoặc để operator vá bằng private patch nếu cần

Điểm chèn Cloudflare, nếu dùng sau này, sẽ nằm ở lớp DNS/WAF/CDN phía trước ingress chứ không thay runtime cluster. Runbook từng bước nằm tại [../docs/CLOUDFLARE.md](../docs/CLOUDFLARE.md).

## Private operator template pack

Repo cũng có tracked template pack để operator copy ra private overlay:

- `k8s/templates/private-operator/staging`
- `k8s/templates/private-operator/prod`

Hai template này kế thừa từ lớp operator công khai tương ứng, rồi thêm đúng các patch cần điền bằng giá trị thật ở môi trường riêng:

- `patch-ingress.yaml`: hostname thật, TLS secret thật, ingress class nếu cluster yêu cầu, và annotations riêng của ingress controller/DNS
- `patch-external-secret.yaml`: `ClusterSecretStore` thật và remote secret key/path thật
- `patch-certificate.yaml`: `ClusterIssuer` thật và DNS names thật
- `patch-configmap.yaml`: `FRONTEND_URL` theo hostname thật của môi trường
- `patch-runtime-overrides.yaml`: ví dụ cho replicas/resources/rolling update của stateless app runtime
- `bootstrap/kustomization.yaml`: render bootstrap jobs vào namespace `campuscore-staging` hoặc `campuscore-prod`

Đây là **template copy-out**, không phải manifest chứa secret thật. Khi triển khai staging/prod thật, hãy copy folder phù hợp sang repo hoặc overlay private, thay toàn bộ placeholder `replace-with-real-*` / `replace-with-private-*`, render lại, rồi mới apply.

Render kiểm tra:

```bash
kubectl kustomize k8s/templates/private-operator/staging
kubectl kustomize k8s/templates/private-operator/staging/bootstrap
kubectl kustomize k8s/templates/private-operator/prod
kubectl kustomize k8s/templates/private-operator/prod/bootstrap
```

Checklist ingress/TLS/secrets chi tiết hơn cho pha staging/prod generic và operator overlays nằm tại [../docs/K8S_HANDOFF.md](../docs/K8S_HANDOFF.md). Nếu dùng domain qua Cloudflare, đi theo [../docs/CLOUDFLARE.md](../docs/CLOUDFLARE.md) sau khi private overlay đã có hostname, ingress class/annotations, `ClusterIssuer`, `ClusterSecretStore`, và remote secret path thật.

## Đổi sang Docker Hub

Base Kustomize hiện trỏ tới GHCR vì đó là registry public đã xác nhận sẵn cho release hiện tại. Nếu muốn chạy bằng Docker Hub:

1. sửa phần `images:` trong [k8s/base/kustomization.yaml](./base/kustomization.yaml), hoặc
2. dùng `kustomize edit set image` / overlay riêng của bạn.

## Lưu ý bảo mật

- Không commit secrets thật vào repo.
- Dùng Docker Hub PAT qua `DOCKERHUB_TOKEN`, không dùng password tài khoản thật trong CI/CD.
- Toàn bộ internal routes `/api/v1/internal/*` vẫn bị chặn ở `nginx`; K8s không thay đổi contract này.
