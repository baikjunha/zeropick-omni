import json
import time
import urllib.parse
import urllib.request

GW = 'http://localhost:8000'
ok = ng = 0

def call(method, path, body=None, timeout=30, base=None):
    path = urllib.parse.quote(path, safe='/?&=:,')
    req = urllib.request.Request((base or GW) + path, method=method,
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

print('── 4. 상품 관리자 CRUD (M7: 등록·수정·삭제 + 원장 연동)')
ts = int(time.time())
st, np_ = call('POST', '/product-service/products',
               {'name': f'검증용 제로스낵 {ts}', 'brand': '옴니푸드', 'category': '과자',
                'price': 1900, 'stock': 7, 'claimType': '제로슈거',
                'kcal': 120, 'sugarG': 0, 'carbG': 20, 'sweeteners': ['에리스리톨']})
pid = (np_ or {}).get('id')
check('상품 등록 201', st == 201 and pid, f'id={pid}')
st, syncres = call('POST', '/stock-service/stocks/sync')
check('원장 동기화(신규 상품 반영)', st == 200 and (syncres or {}).get('created', 0) >= 1,
      f"created={(syncres or {}).get('created')} total={(syncres or {}).get('total')}")
st, ledg = call('GET', f'/stock-service/stocks/{pid}')
check('신규 상품 원장 생성', st == 200 and (ledg or {}).get('onlineStock') == 7,
      f"online={(ledg or {}).get('onlineStock')}")
st, nord = call('POST', '/commerce-service/orders',
                {'memberId': mid, 'items': [{'productId': pid, 'qty': 1}]})
nid = (nord or {}).get('orderId') or (nord or {}).get('id')
st2, npaid = call('POST', f'/commerce-service/orders/{nid}/pay', {'paymentMethod': 'CARD'})
st3, ledg2 = call('GET', f'/stock-service/stocks/{pid}')
check('신규 상품 주문·차감', (npaid or {}).get('status') == 'PAID' and (ledg2 or {}).get('onlineStock') == 6,
      f"7 → {(ledg2 or {}).get('onlineStock')}")
st, upd = call('PUT', f'/product-service/products/{pid}',
               {'name': f'검증용 제로스낵 {ts}', 'brand': '옴니푸드', 'category': '과자',
                'price': 2100, 'stock': 7, 'claimType': '제로슈거',
                'kcal': 120, 'sugarG': 0, 'carbG': 20, 'sweeteners': ['스테비아']})
check('상품 수정 200', st == 200 and (upd or {}).get('price') == 2100,
      f"price={(upd or {}).get('price')}")
st, _ = call('DELETE', f'/product-service/products/{pid}')
st2, _ = call('GET', f'/product-service/products/{pid}')
check('상품 삭제 후 404', st in (200, 204) and st2 == 404, f'DELETE={st} GET={st2}')

print('── 5. 초과주문 2단 방어 (H5 생성 시 동기 확인 + H7 결제 시 조건부 차감)')
st, _ = call('POST', '/commerce-service/orders',
             {'memberId': mid, 'items': [{'productId': 5, 'qty': 99999}]})
check('주문 생성 시 재고 확인 409 (H5)', st == 409)
st, cur = call('GET', '/stock-service/stocks/5')
avail = (cur or {}).get('onlineStock', 0)
if avail < 2:
    call('PUT', '/stock-service/stocks/5/restore', {'qty': 10})
    st, cur = call('GET', '/stock-service/stocks/5')
    avail = (cur or {}).get('onlineStock', 0)
st, race = call('POST', '/commerce-service/orders',
                {'memberId': mid, 'items': [{'productId': 5, 'qty': avail}]})
rid = (race or {}).get('orderId') or (race or {}).get('id')
call('PUT', '/stock-service/stocks/5/deduct', {'qty': 1})
st2, _ = call('POST', f'/commerce-service/orders/{rid}/pay', {'paymentMethod': 'CARD'})
call('PUT', '/stock-service/stocks/5/restore', {'qty': 1})
check('경합 결제 시 조건부 차감 409 + 취소 전이 (H7)', st2 == 409,
      f'가용 {avail} 전량 주문 후 타 채널 선차감')

print('── 6. 행동 이벤트 (M6: Kafka Avro 발행→구독)')
time.sleep(5)
st, reco = call('GET', f'/recommendation-service/recommendations/{mid}')
check('주문 이벤트 기반 추천 생성', st == 200 and reco,
      f'{len(reco or [])}건' + (f" (1위: {reco[0].get('reason','')[:20]})" if reco else ''))

print('── 7. AI 파이프라인 (M10)')
st, chat = call('POST', '/recommendation-service/chat', {'memberId': mid, 'message': '제로 콜라 추천해줘'})
check('챗봇 응답+상품', st == 200 and chat and chat.get('products'),
      f"{len((chat or {}).get('products') or [])}건 fallback={(chat or {}).get('usedFallback')}")

print('── 8. CDC 파이프라인 (H6·H7·H10)')
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

print('── 9. AI 재고 예측 (H선택10)')
st, fc = call('GET', '/recommendation-service/stock-forecast?limit=5')
check('소진 예측 응답', st == 200 and isinstance(fc, list),
      f"{len(fc or [])}건" + (f" (1위 {fc[0].get('daysLeft')}일)" if fc else ''))

print('── 10. 설정 전파 (선택: Spring Cloud Bus)')
st, _ = call('POST', '/actuator/busrefresh', body={}, base='http://localhost:8082')
check('busrefresh 발행', st in (200, 204), f'HTTP {st}')

print(f'\n결과: PASS {ok} / FAIL {ng}')
