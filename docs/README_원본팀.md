# Retail-AI-Shop — 제로픽 (ZeroPick)

저당·제로 식품 전문 커머스 + 조건 기반 개인화 추천 + 상담 챗봇

LG CNS AM Inspire Camp 5기 · 2차 미니 프로젝트 · 과제 **[RTL-M]** (리테일 · 난이도 중)

---

## 1. 서비스 개요

못 먹는 감미료·알레르기·가격대를 등록하면, 조회·구매 행동을 반영해 조건에 맞는 상품을
추천 이유와 함께 제시한다. 상품 목록의 기본 정렬이 추천순이며, 상담 챗봇이 자연어 질의
("말티톨 없는 제로 초콜릿 5천원 이하")에 상품과 근거로 답한다.

| 항목 | 내용 |
|---|---|
| 기간 | 2026-08-13 ~ (40시간) |
| 목표 | 기능 100점 (핵심 10개 70 + 선택 10개 30) |
| 핵심 차별화 기술 (핵심 10번) | LLM 상담 챗봇 — 실패 시 규칙 기반 폴백 |

## 2. 기술 스택

팀 표준: **Spring Boot 4.0.7** (2026-08-13 확정 — start.spring.io 가 현재 4.x 만 제공하고, 인프라 3종이 4.0.7 로 구축·검증됨).
수업·강사 레포(`joneconsulting/new-toy-msa`)는 3.5 기준이므로 수업 코드 복붙 시 아래 주의 참고.

| 구분 | 값 |
|---|---|
| 빌드 | **Maven** (Gradle 아님) |
| Java / Spring Boot / Cloud | 17 / **4.0.7** / **2025.1.2** |
| DB | H2 (개발) / MariaDB (통합) — 서비스별 분리 |
| 메시징 | Kafka + Schema Registry (Avro, BACKWARD) |
| 이미지 | 멀티스테이지 (`maven:3.9.11-eclipse-temurin-17` → `eclipse-temurin:17-jre`) |

> **수업 코드(3.5) 복붙 주의** — Gateway 의존성은 `spring-cloud-starter-gateway-server-webflux`
> (yml 키는 `spring.cloud.gateway.server.webflux.routes` — 강사 레포와 동일). Swagger 는 Boot 4 용 springdoc **3.x**.
> web·data-jpa·validation·kafka·openfeign·config 스타터는 이름 동일 — Boot 4 호환 존재 확인 완료
> (OpenFeign 5.0.2 · Config client 5.0.4 · springdoc 3.1.0).

> **플랫폼 주의** — Mac(Apple Silicon)에서 만든 arm64 이미지는 Windows·EC2에서
> `exec format error`로 즉시 종료된다. 공유 이미지는 `docker buildx build --platform linux/amd64`.

## 3. 서비스 구성 · 포트

| 서비스 | 포트 | 책임 |
|---|---|---|
| `product-service` | 8081 | 상품·영양정보·감미료·재고 |
| `commerce-service` | 8082 | 회원·로그인·장바구니·주문·모의결제(PENDING→PAID)·행동 이벤트 발행 |
| `recommendation-service` | 8083 | 선호 조건·행동 로그·추천 점수·상담 챗봇(LLM+폴백) |
| API Gateway | 8000 | 단일 진입점 |
| Eureka / Config | 8761 / 8888 | |
| Kafka / Schema Registry | 9092 / **8085** | 8081 충돌로 변경 |
| Prometheus / Grafana / Zipkin | 9090 / 3000 / 9411 | |

## 4. 개발 계약 문서 (`docs/`)

**코드 작성 전에 이 세 개부터.** 전부 자동 검증 통과 상태다 (`docs/verify/verify_docs.py`, 38 검사).

| 문서 | 내용 | 검증 |
|---|---|---|
| [제로픽_ERD.dbml](docs/제로픽_ERD.dbml) · [sql/](docs/sql) | 스키마 3개 + 시드 18개. dbml 은 dbdiagram.io 붙여넣기용 | H2(MariaDB 모드) 실행 |
| [API명세서.md](docs/API명세서.md) · [openapi/openapi.yaml](docs/openapi/openapi.yaml) | 경로 22개 (회원·주문취소·behaviors·상품 CRUD·재고 차감/복구 포함) | OpenAPI 3.0.3 검증 |
| [이벤트스키마.md](docs/이벤트스키마.md) · [avro/](docs/avro) | 토픽 3개, 파티션·컨슈머 그룹, 가중치(조회+1/담기0/주문+50) | fastavro 왕복 |

시드: [docs/시드데이터_zerofinder.csv](docs/시드데이터_zerofinder.csv) — 크롤링 515건 (영양·감미료 전 건, 이미지 URL 포함).
수기 입력(1인 25개) 계획은 폐기. 가격 없는 항목은 로딩 시 임의값, 카테고리는 원문 유지 — 5분류로 걸러 쓸지 회의에서 결정.

## 5. 로컬 실행

```bash
# ① 네트워크 (최초 1회)
docker network create ecommerce-network

# ② 아우터 아키텍처 (Config·Eureka·Kafka·Schema Registry·DB)
docker compose -f docker-compose.yml up -d

# ③ 마이크로서비스 (Gateway 는 여기, 맨 마지막)
docker compose -f docker-compose-ms.yml up -d
```

> 기동 순서 주의 — Gateway 가 Config 보다 먼저 뜨면 설정을 못 받아 500 (수업 08-07 확인).
> 스키마는 각 서비스가 JPA 로 생성하거나 `docs/sql/` 실행.

## 6. 협업 규칙

- 브랜치 전략: `feature/*` → **`develop`** 으로 PR 머지(1인 승인, 셀프 머지 금지).
  `main` 은 시연·제출 시점에 develop 을 머지하는 안정 브랜치 — 직접 push 금지.
- 브랜치: `<type>/<서비스>-<기능>` — 예: `feat/reco-kafka-consumer`, `chore/product-init`
- 커밋: `<type>: <설명>` — feat / fix / refactor / chore / docs / test
- 금지: `.env`·LLM API 키 커밋 (키는 Config Server), `--force`

## 7. 팀 (A안 — 1인 1서비스)

| 역할 | 담당 |
|---|---|
| PM · 프론트 · 산출물 | 백준하 |
| 부팀장 · 인프라 + `product-service` | 김지현 |
| `commerce-service` (회원·주문·결제·이벤트 발행) | 김도현 |
| `recommendation-service` (추천·챗봇/AI) | 이경민 |

인프라(Eureka·Gateway·Config·compose)는 김지현 님이 D1~D2 선행 구축, 이후 product-service 담당.

기획서·프로토타입(동작 데모)·산출물은 팀 공유 폴더 참고.
