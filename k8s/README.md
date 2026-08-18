# K8s 배포 (선택 1)

로컬 검증 기준: Docker Desktop → Settings → Kubernetes → Enable Kubernetes.

```bash
# 1. 이미지 빌드·태깅 (compose 빌드 재사용)
docker compose build
for s in service-discovery config-service apigateway-service product-service commerce-service recommendation-service stock-service pos-sync-service; do
  docker tag zeropick-omni-$s zeropick/$s:local   # compose 프로젝트명에 따라 접두어 확인
done

# 2. LLM 시크릿 (값은 채널로만 공유)
kubectl apply -f k8s/00-namespace.yaml
kubectl -n zeropick create secret generic zeropick-llm --from-literal=LLM_API_KEY=<키>

# 3. 배포 (번호 순서대로)
kubectl apply -f k8s/

# 4. 확인
kubectl -n zeropick get pods            # 전부 Running
curl http://localhost:30800/product-service/products   # NodePort 게이트웨이
```

- 이미지가 로컬 데몬에 있으므로 `imagePullPolicy: Never` — Docker Desktop K8s 는 도커 이미지를 공유한다.
- EC2 배포 시에는 이미지 태그를 Docker Hub 경로로 바꾸고 `imagePullPolicy: IfNotPresent` 로 변경.

CDC 인프라(pos-db, settlement-db, cdc-connect)는 `40-cdc.yaml` 에 포함된다.
커넥터 등록은 compose 때와 같이 cdc-connect 서비스(:8083)에 REST 로 수행한다.
매니페스트는 작성 완료 상태이며, 로컬 클러스터 배포 검증은 아직 수행하지 않았다.
