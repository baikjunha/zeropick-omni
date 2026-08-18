import json
import statistics
import time
import urllib.parse
import urllib.request

GW = 'http://localhost:8000'

def call(method, path, body=None, timeout=60):
    path = urllib.parse.quote(path, safe='/?&=:,')
    req = urllib.request.Request(GW + path, method=method,
                                 headers={'Content-Type': 'application/json'})
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    t0 = time.perf_counter()
    with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
        raw = r.read().decode('utf-8', 'ignore')
        ms = (time.perf_counter() - t0) * 1000
        return ms, (json.loads(raw) if raw.strip() else None)

def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))]

def sweetener_names(detail):
    out = []
    for s in (detail or {}).get('sweeteners') or []:
        out.append(s if isinstance(s, str) else (s.get('name') or ''))
    return out

def judge(products, exclude=None, max_price=None, keyword=None):
    good = 0
    for p in products:
        pid = p.get('id') or p.get('productId')
        try:
            _, d = call('GET', f'/product-service/products/{pid}')
        except Exception:
            continue
        okay = True
        if exclude and any(exclude in n for n in sweetener_names(d)):
            okay = False
        if max_price and (d.get('price') or 0) > max_price:
            okay = False
        if keyword and keyword not in ((d.get('category') or '') + (d.get('name') or '')):
            okay = False
        good += okay
    return good, len(products)

_, member = call('POST', '/commerce-service/members',
                 {'email': f'measure{int(time.time())}@test.com', 'password': 'pw1234!', 'name': '측정계정'})
mid = member.get('memberId') or member.get('id')

print('── 추천 API 응답시간 (30회)')
call('GET', f'/recommendation-service/recommendations/{mid}')
times = []
for _ in range(30):
    ms, _r = call('GET', f'/recommendation-service/recommendations/{mid}')
    times.append(ms)
print(f'  avg {statistics.mean(times):.0f}ms / p50 {pct(times, 0.5):.0f}ms / p95 {pct(times, 0.95):.0f}ms / max {max(times):.0f}ms')

print('── 챗봇 (LLM 실경로) 응답시간·정확도')
CASES = [
    ('아스파탐 안 들어간 콜라', {'exclude': '아스파탐', 'keyword': '콜라'}),
    ('1만원 이하 저당 아이스크림', {'max_price': 10000, 'keyword': '아이스크림'}),
    ('말티톨 없는 제로 초콜릿', {'exclude': '말티톨', 'keyword': '초콜릿'}),
    ('수크랄로스 빼고 제로 음료 추천해줘', {'exclude': '수크랄로스', 'keyword': '음료'}),
    ('5천원 이하 제로 과자', {'max_price': 5000, 'keyword': '과자'}),
]
chat_times, acc_good, acc_total, fallbacks = [], 0, 0, 0
for msg, rule in CASES:
    ms, res = call('POST', '/recommendation-service/chat', {'memberId': mid, 'message': msg})
    chat_times.append(ms)
    products = (res or {}).get('products') or []
    fallbacks += 1 if (res or {}).get('usedFallback') else 0
    g, t = judge(products, **rule)
    acc_good += g
    acc_total += t
    print(f'  [{ms:5.0f}ms] {msg} — 상품 {t}건 중 조건 부합 {g}건 fallback={(res or {}).get("usedFallback")}')
print(f'  응답시간 avg {statistics.mean(chat_times):.0f}ms / max {max(chat_times):.0f}ms, '
      f'조건 부합률 {acc_good}/{acc_total} ({100 * acc_good / max(1, acc_total):.0f}%), 폴백 {fallbacks}/{len(CASES)}회')

print('── 자연어 검색 응답시간')
search_times = []
for q in ['말티톨 없는 제로 초콜릿', '아스파탐 없는 음료', '1만원 이하 저당 과자']:
    ms, res = call('POST', '/recommendation-service/search', {'query': q})
    n = (res or {}).get('totalCount') or len((res or {}).get('products') or [])
    search_times.append(ms)
    print(f'  [{ms:5.0f}ms] {q} — {n}건')
print(f'  avg {statistics.mean(search_times):.0f}ms')
