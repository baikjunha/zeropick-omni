// 선택 8: 추천 API 동시 요청 부하 테스트 (k6)
// 실행: docker run --rm -i -v "$PWD/loadtest:/scripts" grafana/k6:0.52.0 run /scripts/reco-load.js
//       --summary-export=/scripts/summary.json
import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  stages: [
    { duration: '20s', target: 10 },   // 워밍업
    { duration: '30s', target: 30 },   // 증가
    { duration: '60s', target: 30 },   // 유지 — 동시 30 사용자
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // p95 1초 이내
    http_req_failed: ['rate<0.01'],    // 실패율 1% 미만
  },
}

const BASE = 'http://host.docker.internal:8000'

export default function () {
  const reco = http.get(`${BASE}/recommendation-service/recommendations/1?limit=20`)
  check(reco, { '추천 200': (r) => r.status === 200 })

  const list = http.get(`${BASE}/product-service/products?sugarMax=5`)
  check(list, { '목록 200': (r) => r.status === 200 })

  sleep(0.3)
}
