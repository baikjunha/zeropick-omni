import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '30s', target: 30 },
    { duration: '60s', target: 30 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
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
