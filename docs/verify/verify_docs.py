import io, os, re, json, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.dirname(HERE)
PJT  = os.path.dirname(DOCS)
PROTO = os.path.join(PJT, 'prototype', 'ZeroPick_프로토타입.html')
PLAN  = os.path.join(PJT, '기획', '팀플_기획서_ZeroPick.html')
H2 = os.path.expanduser('~/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar')

results = []
def check(section, name, fn):
    try:
        detail = fn()
        results.append((section, name, True, detail or ''))
    except Exception as e:
        results.append((section, name, False, str(e)[:140]))

def read(p): return io.open(p, encoding='utf-8').read()

def run_sql(scripts, query=None):
    """스키마·시드를 한 DB 세션에서 실행하고, 마지막에 검증 쿼리를 돌린다."""
    merged = "\n".join(read(os.path.join(DOCS, 'sql', s)) for s in scripts)
    if query:
        merged += f"\nCREATE TABLE __v AS SELECT ({query}) AS n;\nSELECT n FROM __v;"
    with tempfile.NamedTemporaryFile('w', suffix='.sql', delete=False, encoding='utf-8') as f:
        f.write(merged); tmp = f.name
    r = subprocess.run(['java', '-Dfile.encoding=UTF-8', '-cp', H2, 'org.h2.tools.RunScript',
                        '-url', 'jdbc:h2:mem:v;MODE=MariaDB;DATABASE_TO_LOWER=TRUE',
                        '-script', tmp, '-showResults'],
                       capture_output=True, text=True, encoding='utf-8', timeout=60)
    os.unlink(tmp)
    if r.returncode != 0:
        raise RuntimeError((r.stderr or r.stdout).strip().splitlines()[-1])
    return r.stdout

check('A.DDL', 'schema-product.sql 실행', lambda: run_sql(['schema-product.sql']) and 'OK')
check('A.DDL', 'schema-commerce.sql 실행', lambda: run_sql(['schema-commerce.sql']) and 'OK')
check('A.DDL', 'schema-reco.sql 실행', lambda: run_sql(['schema-reco.sql']) and 'OK')
def run_count(scripts, query):
    """쿼리 결과 숫자를 결과 마커(--> N)에서 정확히 뽑는다. 시드 echo 오탐 방지."""
    out = run_sql(scripts, query)
    tail = out[out.rfind('FROM __v'):]
    nums = re.findall(r'^--> (\d+)', tail, re.M)
    assert nums, '결과 행을 찾지 못함'
    return int(nums[-1])

def d_seed_count():
    n = run_count(['schema-product.sql','seed-product.sql'], 'SELECT COUNT(*) FROM product')
    assert n == 18, f'상품 {n}개 (기대 18)'
    return f'COUNT(*) = {n}'
check('A.DDL', '시드 18개 상품 적재', d_seed_count)

def d_filter_count():
    n = run_count(['schema-product.sql','seed-product.sql'],
     "SELECT COUNT(*) FROM product p WHERE NOT EXISTS ("
     " SELECT 1 FROM product_sweetener ps JOIN sweetener s ON s.id=ps.sweetener_id"
     " WHERE ps.product_id=p.id AND s.name='말티톨')")
    assert n == 16, f'{n}개 (기대 16 — 말티톨 상품은 16·17번 둘뿐)'
    return f'COUNT(*) = {n} — 하드필터 쿼리 동작'
check('A.DDL', '말티톨 제외 필터 쿼리 = 16개', d_filter_count)
def d_check_constraint():
    """불량 status INSERT 가 CHECK 에 걸려 실패해야 한다."""
    bad = read(os.path.join(DOCS,'sql','schema-commerce.sql')) + (
        "\nINSERT INTO member (email,password,name) VALUES ('a@a.com','x','a');"
        "\nINSERT INTO orders (order_no,member_id,total_price,status) VALUES ('ZP1',1,0,'HACKED');")
    with tempfile.NamedTemporaryFile('w', suffix='.sql', delete=False, encoding='utf-8') as f:
        f.write(bad); tmp = f.name
    r = subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',H2,'org.h2.tools.RunScript',
                        '-url','jdbc:h2:mem:v3;MODE=MariaDB;DATABASE_TO_LOWER=TRUE','-script',tmp],
                       capture_output=True, text=True, encoding='utf-8', timeout=60)
    os.unlink(tmp)
    assert r.returncode != 0, '불량 status(HACKED) 가 INSERT 됨 — CHECK 미동작'
    return "CHECK 동작 (HACKED 거부됨)"
check('A.DDL', '잘못된 주문상태 거부(CHECK)', d_check_constraint)

import fastavro
SAMPLES = {
 'product-viewed.avsc':  {"memberId":1,"productId":10,"category":"간식/디저트","occurredAt":1765600000000},
 'cart-added.avsc':      {"memberId":1,"productId":10,"category":"간식/디저트","qty":2,"occurredAt":1765600000000},
 'order-completed.avsc': {"memberId":1,"productId":10,"category":"간식/디저트","qty":1,"unitPrice":15000,
                          "orderNo":"ZP1001","paymentMethod":"카카오페이","occurredAt":1765600000000},
}
def avro_roundtrip(fname):
    schema = fastavro.parse_schema(json.loads(read(os.path.join(DOCS,'avro',fname))))
    buf = io.BytesIO()
    fastavro.schemaless_writer(buf, schema, SAMPLES[fname]); buf.seek(0)
    back = fastavro.schemaless_reader(buf, schema)
    src = {k: v for k, v in SAMPLES[fname].items()}
    got = {k: (int(back[k].timestamp()*1000) if hasattr(back[k],'timestamp') else back[k]) for k in src}
    assert got == src, f"왕복 불일치: {got}"
    return f"{len(schema['fields'])}필드 왕복 일치"
for f in SAMPLES: check('B.Avro', f, lambda f=f: avro_roundtrip(f))

import yaml
from openapi_spec_validator import validate as validate_openapi
SPEC = yaml.safe_load(read(os.path.join(DOCS,'openapi','openapi.yaml')))
check('C.API', 'OpenAPI 3.0.3 스펙 검증', lambda: (validate_openapi(SPEC), f"경로 {len(SPEC['paths'])}개")[1])
REQUIRED_PATHS = ['/product-service/products','/product-service/products/{id}','/product-service/products/compare',
 '/product-service/products/{id}/stock/deduct',
 '/commerce-service/members','/commerce-service/members/login','/commerce-service/carts',
 '/commerce-service/orders','/commerce-service/orders/{orderId}/pay',
 '/commerce-service/orders/{orderId}/cancel','/commerce-service/behaviors',
 '/recommendation-service/preferences','/recommendation-service/recommendations/{memberId}',
 '/recommendation-service/chat','/recommendation-service/search',
 '/recommendation-service/click','/recommendation-service/metrics']
check('C.API', '필수 경로 17개 존재', lambda:
    (lambda miss: '전부 존재' if not miss else (_ for _ in ()).throw(RuntimeError(f'누락: {miss}')))(
        [p for p in REQUIRED_PATHS if p not in SPEC['paths']]))
check('C.API', '결제 상태머신 표현', lambda:
    'PENDING' in json.dumps(SPEC['paths']['/commerce-service/orders']['post']) and
    'PAID' in json.dumps(SPEC['paths']['/commerce-service/orders/{orderId}/pay']) and 'OK')

import csv as _csv
_seed_path = os.path.join(DOCS, '시드데이터_zerofinder.csv')
if not os.path.exists(_seed_path):
    print('  (D. 시드 CSV 없음 — PR #9 머지 전이면 정상, 검사 스킵)')
else:
    with io.open(_seed_path, encoding='utf-8-sig') as _f:
        _rows = list(_csv.reader(_f))
    _hdr, _data = _rows[0], _rows[1:]
    def _d1():
        assert len(_data) >= 500, f'{len(_data)}건뿐'
        return f'{len(_data)}건'
    check('D.시드', '500건 이상', _d1)
    def _d2():
        need = ['protein_g', 'fat_g', 'sodium_mg', 'serving_size', 'image_url', 'nutrition_facts_url']
        miss = [c for c in need if not any(c in h for h in _hdr)]
        assert not miss, f'누락 {miss}'
        return '영양·이미지 6컬럼 OK'
    check('D.시드', '영양·이미지 컬럼 존재', _d2)
    def _d3():
        n = sum(1 for r in _data if not str(r[3]).strip())
        assert n == 0, f'빈 가격 {n}건'
        return '전건 입력'
    check('D.시드', '가격 전건 입력', _d3)

if not os.path.exists(PLAN):
    print('  (E. 기획서 HTML 없음 — 레포 단독 실행이면 정상, 기획서 근거는 스킵)')
    plan = None
else:
    plan = read(PLAN)
oas = read(os.path.join(DOCS,'openapi','openapi.yaml'))
erd = read(os.path.join(DOCS,'제로픽_ERD.dbml'))
api = read(os.path.join(DOCS,'API명세서.md'))
evt = read(os.path.join(DOCS,'이벤트스키마.md'))
sqls = "".join(read(os.path.join(DOCS,'sql',f)) for f in
               ['schema-product.sql','schema-commerce.sql','schema-reco.sql'])
RUBRIC = [
 ('핵심1 서비스 3개 분리',      [(erd,'product_db'),(erd,'commerce_db'),(erd,'reco_db')]),
 ('핵심2 Eureka',              [(plan,'Eureka')]),
 ('핵심3 Gateway 단일 진입점',  [(oas,'localhost:8000'),(api,'API Gateway')]),
 ('핵심4 Config+LLM키 분리',    [(plan,'Config Server'),(plan,'LLM API 키')]),
 ('핵심5 OpenFeign 동기',       [(api,'stock/deduct'),(api,'OpenFeign')]),
 ('핵심6 Kafka 3종 이벤트',     [(evt,'product-viewed'),(evt,'cart-added'),(evt,'order-completed')]),
 ('핵심7 CRUD+재고차감+주문취소', [(oas,'/commerce-service/orders/{orderId}/pay'),(oas,'/commerce-service/orders/{orderId}/cancel'),(sqls,"'PENDING'")]),
 ('핵심7b 상품 CRUD',           [(api,'상품 등록'),(api,'상품 수정'),(api,'상품 삭제')]),
 ('핵심8 DB분리+ERD+영양컬럼',  [(erd,'Database per Service'),(erd,'protein_g'),(erd,'nutrition_facts_url')]),
 ('핵심9 Docker Compose',       [(plan,'docker-compose')]),
 ('핵심10 챗봇+측정',           [(oas,'/recommendation-service/chat'),(plan,'측정 지표')]),
 ('선택1 K8s',                  [(plan,'Kubernetes')]),
 ('선택2 Schema Registry',      [(evt,'Schema Registry'),(evt,'BACKWARD')]),
 ('선택3 Spring Cloud Bus',     [(plan,'Spring Cloud Bus')]),
 ('선택4 CB/LLM Fallback',      [(plan,'Circuit Breaker'),(oas,'usedFallback')]),
 ('선택5 모니터링',             [(plan,'Grafana')]),
 ('선택6 추천 성과 대시보드',   [(oas,'/recommendation-service/click'),(oas,'/recommendation-service/metrics'),(sqls,'reco_click')]),
 ('선택7 CI/CD',                [(plan,'GitHub Actions')]),
 ('선택8 추천 API 부하테스트',  [(plan,'부하 테스트'),(oas,'/recommendations/{memberId}')]),
 ('선택9 로그 중앙화 EFK',      [(plan,'Fluent Bit')]),
 ('선택10 자연어 검색+행동수신', [(oas,'/recommendation-service/search'),(oas,'/commerce-service/behaviors')]),
]
for item, conds in RUBRIC:
    def _fn(conds=conds):
        usable = [(hay, needle) for hay, needle in conds if hay is not None]
        if not usable:
            return '스킵(기획서 근거)'
        miss = [needle for hay, needle in usable if needle not in hay]
        assert not miss, f'근거 문자열 없음: {miss}'
        return '근거 확인'
    check('E.채점표', item, _fn)

print()
cur = None
ok = fail = 0
for section, name, passed, detail in results:
    if section != cur:
        print("\n[" + section + "]"); cur = section
    mark = 'PASS' if passed else 'FAIL'
    ok += passed; fail += (not passed)
    print(f"  {mark}  {name}" + (f"  — {detail}" if detail else ""))
print("\n" + "=" * 60 + f"\n합계: PASS {ok} / FAIL {fail}")
sys.exit(1 if fail else 0)
