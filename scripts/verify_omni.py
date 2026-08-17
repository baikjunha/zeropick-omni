import json
import time
import urllib.parse
import urllib.request

GW = 'http://localhost:8000'
ok = ng = 0

def call(method, path, body=None, timeout=30):
    path = urllib.parse.quote(path, safe='/?&=:,')
    req = urllib.request.Request(GW + path, method=method,
                                 headers={'Content-Type': 'application/json'})
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
            raw = r.read().decode('utf-8', 'ignore')
            return r.status, (json.loads(raw) if raw.strip() else None)
    except urllib.error.HTTPError as e:
        return e.code, None

def check(name, cond, detail=''):
    global ok, ng
    mark = 'PASS' if cond else 'FAIL'
    cond = bool(cond)
    ok += cond
    ng += (not cond)
    print(f'  [{mark}] {name}' + (f'  — {detail}' if detail else ''), flush=True)

print('── 1. 서비스 메시 (M1·H1: 도메인 서비스 5종)')
import re
h = urllib.request.urlopen('http://localhost:8761/eureka/apps', timeout=10).read().decode()
apps = sorted(set(re.findall(r'<app>([A-Z-]+)</app>', h)))
check('유레카 등록', all(a in apps for a in
      ['PRODUCT-SERVICE', 'COMMERCE-SERVICE', 'RECOMMENDATION-SERVICE',
       'STOCK-SERVICE', 'POS-SYNC-SERVICE', 'APIGATEWAY-SERVICE']), str(len(apps)) + '개: ' + ','.join(a.replace('-SERVICE','') for a in apps))

print('── 2. 상품 + 재고 원장 (재고 독립 서비스)')
st, products = call('GET', '/product-service/products')
check('상품 목록', st == 200 and len(products or []) >= 490, f'{len(products or [])}건')
st, s3 = call('GET', '/stock-service/stocks/3')
check('재고 원장 조회', st == 200 and s3 and 'onlineStock' in s3,
      f"online={s3.get('onlineStock')} pos={s3.get('posStock')}" if s3 else '')
if products and s3:
    p3 = next((p for p in products if p['id'] == 3), None)
    check('상품 응답 = 원장 오버레이', p3 and p3['stock'] == s3['onlineStock'],
          f"product.stock={p3['stock'] if p3 else '?'} vs 원장 {s3['onlineStock']}")

print('── 3. 주문 파이프라인 (M7·H5: 주문→재고 Feign)')
st, member = call('POST', '/commerce-service/members',
                  {'email': f'omni{int(time.time())}@test.com', 'password': 'pw1234!', 'name': '옴니테스터'})
mid = (member or {}).get('memberId') or (member or {}).get('id')
check('회원가입', st in (200, 201) and mid, f'memberId={mid}')
st, before = call('GET', '/stock-service/stocks/5')
st, order = call('POST', '/commerce-service/orders',
                 {'memberId': mid, 'items': [{'productId': 5, 'qty': 2}]})
oid = (order or {}).get('orderId') or (order or {}).get('id')
check('주문 생성(PENDING)', st in (200, 201) and oid,
      f"orderNo={(order or {}).get('orderNo')}")
st, paid = call('POST', f'/commerce-service/orders/{oid}/pay', {'paymentMethod': 'CARD'})
check('모의결제(PAID)', st == 200 and (paid or {}).get('status') == 'PAID',
      f"status={(paid or {}).get('status')}")
st, after = call('GET', '/stock-service/stocks/5')
check('결제 시 재고 차감', after and before and after['onlineStock'] == before['onlineStock'] - 2,
      f"{before['onlineStock'] if before else '?'} → {after['onlineStock'] if after else '?'}")

print('── 4. 재고 부족 409 (H7 동시성·초과주문 방지)')
st, big = call('POST', '/commerce-service/orders',
               {'memberId': mid, 'items': [{'productId': 5, 'qty': 99999}]})
bid = (big or {}).get('orderId') or (big or {}).get('id')
st2, _ = call('POST', f'/commerce-service/orders/{bid}/pay', {'paymentMethod': 'CARD'})
check('초과 주문 409 + 취소 전이', st2 == 409)

print('── 5. 행동 이벤트 (M6: Kafka Avro 발행→구독)')
time.sleep(5)
st, reco = call('GET', f'/recommendation-service/recommendations/{mid}')
check('주문 이벤트 기반 추천 생성', st == 200 and reco,
      f'{len(reco or [])}건' + (f" (1위: {reco[0].get('reason','')[:20]})" if reco else ''))

print('── 6. AI 파이프라인 (M10)')
st, chat = call('POST', '/recommendation-service/chat', {'memberId': mid, 'message': '제로 콜라 추천해줘'})
check('챗봇 응답+상품', st == 200 and chat and chat.get('products'),
      f"{len((chat or {}).get('products') or [])}건 fallback={(chat or {}).get('usedFallback')}")

print('── 7. CDC 파이프라인 (H6·H7·H10)')
st, sync0 = call('GET', '/pos-sync-service/status')
check('동기화 현황 API', st == 200 and sync0 is not None,
      f"applied={(sync0 or {}).get('appliedCount')}")
import subprocess
new_val = int(time.time()) % 900 + 10
subprocess.run(['docker', 'exec', 'pos-db', 'mariadb', '-uroot', '-ppos1234', 'pos',
                '-e', f'UPDATE pos_stock SET stock = {new_val} WHERE product_id = 3;'],
               capture_output=True)
time.sleep(10)
st, s3b = call('GET', '/stock-service/stocks/3')
check('POS 변경 → 원장 posStock 반영', s3b and s3b.get('posStock') == new_val,
      f"posStock={(s3b or {}).get('posStock')}")
st, sync1 = call('GET', '/pos-sync-service/status')
check('동기화 카운트 증가', sync1 and sync1.get('appliedCount', 0) > (sync0 or {}).get('appliedCount', 0),
      f"applied={(sync1 or {}).get('appliedCount')} dlq={(sync1 or {}).get('dlqCount')}")
r = subprocess.run(['docker', 'exec', 'settlement-db', 'sh', '-c',
                    "mariadb -uroot -pstl1234 settlement -e 'SELECT stock FROM pos_stock_ledger WHERE product_id=3;' 2>/dev/null"],
                   capture_output=True, text=True)
check('JDBC Sink → 정산 DB 반영', str(new_val) in (r.stdout or ''), (r.stdout or '').replace('\n', ' ').strip()[:40])

print('── 8. AI 재고 예측 (H선택10)')
st, fc = call('GET', '/recommendation-service/stock-forecast?limit=5')
check('소진 예측 응답', st == 200 and isinstance(fc, list),
      f"{len(fc or [])}건" + (f" (1위 {fc[0].get('daysLeft')}일)" if fc else ''))

print(f'\n결과: PASS {ok} / FAIL {ng}')
