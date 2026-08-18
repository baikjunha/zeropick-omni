import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8000';

export const options = {
  scenarios: {
    online_orders: {
      executor: 'constant-vus',
      exec: 'onlineOrder',
      vus: 20,
      duration: '60s',
    },
    pos_channel: {
      executor: 'constant-vus',
      exec: 'posUpdate',
      vus: 10,
      duration: '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  const res = http.post(`${BASE}/commerce-service/members`, JSON.stringify({
    email: `k6-${Date.now()}@load.test`, password: 'pw1234!', name: 'k6loader',
  }), { headers: { 'Content-Type': 'application/json' } });
  const body = res.json() || {};
  return { memberId: body.memberId || body.id || 1 };
}

export function onlineOrder(data) {
  const productId = 1 + Math.floor(Math.random() * 5);
  const create = http.post(`${BASE}/commerce-service/orders`, JSON.stringify({
    memberId: data.memberId, items: [{ productId, qty: 1 }],
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'order_create' } });

  const ok409 = create.status === 409;
  check(create, { 'create 201/409': (r) => r.status === 201 || r.status === 200 || r.status === 409 });
  if (!ok409 && create.status < 300) {
    const oid = (create.json() || {}).orderId || (create.json() || {}).id;
    const pay = http.post(`${BASE}/commerce-service/orders/${oid}/pay`, JSON.stringify({
      paymentMethod: 'CARD',
    }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'order_pay' } });
    check(pay, { 'pay 200/409': (r) => r.status === 200 || r.status === 409 });
    if (pay.status === 200) {
      http.put(`${BASE}/stock-service/stocks/${productId}/restore`, JSON.stringify({ qty: 1 }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'restore' } });
    }
  }
  sleep(0.2);
}

export function posUpdate() {
  const productId = 1 + Math.floor(Math.random() * 5);
  const posStock = 10 + Math.floor(Math.random() * 900);
  const res = http.put(`${BASE}/stock-service/stocks/${productId}/pos`, JSON.stringify({
    posStock, storeCode: 'GANGNAM01',
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'pos_apply' } });
  check(res, { 'pos 200': (r) => r.status === 200 });
  sleep(0.5);
}
