import axios from 'axios'
import { seedProducts } from './data/seed.js'
import * as store from './utils/productStore.js'
import * as mock from './utils/mock.js'
import { toViewProduct, toViewProducts, toApiProduct, toApiPreference } from './utils/normalize.js'

// 챗봇 카드가 상품명·가격을 그려야 해서, 목록 응답을 id → {name, price} 로 캐시해 둔다.
const productCache = new Map()
function cacheProducts(list) {
  if (Array.isArray(list)) list.forEach((p) => productCache.set(p.id, { name: p.name, price: p.price }))
  return list
}
function lookupProduct(id) {
  return productCache.get(id) || store.getProducts().find((p) => p.id === id)
    || { name: `상품 ${id}`, price: 0 }
}

// 게이트웨이 라우팅 규칙(/<service-name>/**) 그대로 호출한다.
// 개발 서버에서는 vite 프록시가 :8000 으로 넘긴다 (vite.config.js).
const api = axios.create({
  headers: { 'Content-Type': 'application/json' },
  timeout: 4000,
})

api.interceptors.request.use((config) => {
  const member = JSON.parse(sessionStorage.getItem('zp_member') || 'null')
  if (member?.token) config.headers.Authorization = `Bearer ${member.token}`
  return config
})

// 백엔드가 아직 안 떠 있으면 시드 데이터로 폴백한다.
// 어떤 호출이든 한 번 실패하면 mockMode 로 표시해 화면에 배너를 띄운다.
let mockMode = false
const mockListeners = new Set()
export const isMock = () => mockMode
export const onMockChange = (fn) => { mockListeners.add(fn); return () => mockListeners.delete(fn) }
function setMock(v = true) {
  if (mockMode !== v) { mockMode = v; mockListeners.forEach((fn) => fn(v)) }
}

// vite 프록시는 게이트웨이가 죽어 있으면 에러 대신 index.html 을 200 으로 돌려준다.
// JSON 이 아닌 응답은 "백엔드 없음"으로 간주해 폴백한다.
function isJsonResponse(res) {
  const ct = String(res.headers?.['content-type'] || '')
  return ct.includes('application/json') || (typeof res.data === 'object' && res.data !== null)
}

async function tryApi(call, fallback) {
  try {
    const res = await call()
    if (!isJsonResponse(res)) { setMock(true); return fallback() }
    setMock(false)   // 진짜 백엔드 JSON 응답 — 폴백 모드 해제
    return res.data
  } catch (e) {
    if (!e.response || e.response.status >= 500) {
      setMock(true)  // 네트워크 단절·프록시/서버 5xx 는 폴백
      return fallback()
    }
    throw e          // 4xx(401·409 등)는 실제 업무 응답 — 화면 로직이 처리
  }
}

/* ── product-service ── */
export const fetchProducts = (params) =>
  tryApi(() => api.get('/product-service/products', { params }),
    () => mock.filterProducts(store.getProducts(), params))
    .then((list) => cacheProducts(toViewProducts(list)))

export const fetchProduct = (id) =>
  tryApi(() => api.get(`/product-service/products/${id}`),
    () => store.getProducts().find((p) => p.id === Number(id)))
    .then(toViewProduct)

export const compareProducts = (ids) =>
  tryApi(() => api.get('/product-service/products/compare', { params: { ids: ids.join(',') } }),
    () => store.getProducts().filter((p) => ids.includes(p.id)))
    .then(toViewProducts)

export const createProduct = (body) =>
  tryApi(() => api.post('/product-service/products', toApiProduct(body)), () => store.addProduct(body))
    .then(toViewProduct)

export const updateProduct = (id, body) =>
  tryApi(() => api.put(`/product-service/products/${id}`, toApiProduct(body)), () => store.updateProduct(id, body))
    .then(toViewProduct)

export const deleteProduct = (id) =>
  tryApi(() => api.delete(`/product-service/products/${id}`), () => store.deleteProduct(id))

/* ── commerce-service ── */
export const join = (body) =>
  tryApi(() => api.post('/commerce-service/members', body), () => mock.join(body))

export const login = (body) =>
  tryApi(() => api.post('/commerce-service/members/login', body), () => mock.login(body))

export const createOrder = (body) =>
  tryApi(() => api.post('/commerce-service/orders', body), () => mock.createOrder(body))

export const payOrder = (orderId, body) =>
  tryApi(() => api.post(`/commerce-service/orders/${orderId}/pay`, body), () => mock.payOrder(orderId, body))

export const cancelOrder = (orderId) =>
  tryApi(() => api.post(`/commerce-service/orders/${orderId}/cancel`), () => mock.cancelOrder(orderId))

export const fetchOrders = (memberId) =>
  tryApi(() => api.get('/commerce-service/orders', { params: { memberId } }), () => mock.fetchOrders())

// 조회 행동 이벤트 전송 (POST /commerce-service/behaviors — PR #12 계약).
// 담기·주문 이벤트는 장바구니·결제 API 처리 중 백엔드가 직접 발행하므로 보내지 않는다.
export const postBehavior = (ev) =>
  api.post('/commerce-service/behaviors', {
    memberId: ev.memberId,
    productId: ev.productId,
    eventType: ev.type,
    category: ev.cat,
    occurredAt: ev.at,
  }).catch(() => {})

/* ── recommendation-service ── */
export const savePreferences = (memberId, prefs) =>
  tryApi(() => api.post('/recommendation-service/preferences', toApiPreference(memberId, prefs)), () => prefs)

export const fetchRecommendations = (memberId) =>
  tryApi(() => api.get(`/recommendation-service/recommendations/${memberId}`),
    () => mock.recommend(store.getProducts()))

// 백엔드 ChatResponse(answer + RecoItem[])를 위젯 모델(reply + 상품 카드)로 변환한다.
// LLM 왕복(평균 3.3초, p95 6초)이 기본 타임아웃(4초)을 넘을 수 있어 챗봇만 넉넉히 잡는다
export const chat = (body) =>
  tryApi(() => api.post('/recommendation-service/chat', body, { timeout: 25000 }),
    () => mock.chat(store.getProducts(), body.message))
    .then((res) => {
      if (!res || res.answer === undefined) return res
      return {
        reply: res.answer,
        usedFallback: res.usedFallback,
        products: (res.products || []).map((ri) => ({ productId: ri.productId, ...lookupProduct(ri.productId) })),
      }
    })

// 백엔드 MetricsResponse 를 관리자 화면 필드명으로 변환한다.
// 실제 백엔드(#33)는 clickThroughRate/fallbackRate 를 이미 %(0~100)로 내려주고,
// 행동 이벤트 집계·응답시간(avg/p95)은 제공하지 않는다(행동 수치는 로컬 이벤트로 보완).
export const fetchMetrics = () =>
  tryApi(() => api.get('/recommendation-service/metrics'), () => mock.metrics())
    .then((m) => {
      if (!m) return m
      if (m.clickThroughRate !== undefined) {
        const local = mock.metrics()   // viewed/cartAdded/ordered 는 로컬 이벤트 기준
        return {
          viewed: local.viewed, cartAdded: local.cartAdded, ordered: local.ordered,
          conversionRate: local.conversionRate,
          recoClicks: m.totalClicks ?? 0,
          clickRate: m.clickThroughRate ?? 0,          // 이미 % — 재환산 금지
          fallbackRate: m.fallbackRate ?? 0,           // 이미 %
          chatCount: m.totalChatRequests ?? 0,
          avgLatencyMs: null, chatAvgMs: null, chatP95Ms: null,  // 백엔드 미제공
        }
      }
      if (m.ctr === undefined) return m                // mock 폴백은 그대로 통과
      const ev = m.eventCounts || {}
      const viewed = ev.PRODUCT_VIEWED ?? 0
      return {
        viewed,
        cartAdded: ev.CART_ADDED ?? 0,
        ordered: ev.ORDER_COMPLETED ?? 0,
        conversionRate: viewed ? Math.round(((ev.ORDER_COMPLETED ?? 0) / viewed) * 1000) / 10 : 0,
        recoClicks: m.clicks ?? 0,
        clickRate: Math.round((m.ctr ?? 0) * 1000) / 10,
        fallbackRate: Math.round((m.fallbackRate ?? 0) * 1000) / 10,
        avgLatencyMs: m.avgRecoMs ?? null,
        chatAvgMs: m.avgChatMs ?? null,
        chatP95Ms: m.p95ChatMs ?? null,
        chatCount: m.chatCount ?? 0,
      }
    })

// 추천 상품 클릭 — 클릭률(선택 6) 집계용. 실패 시 로컬 기록으로 폴백.
export const postRecoClick = (body) =>
  api.post('/recommendation-service/click', body).catch(() => {
    try {
      const all = JSON.parse(localStorage.getItem('zp_reco_clicks') || '[]')
      all.push({ ...body, at: new Date().toISOString() })
      localStorage.setItem('zp_reco_clicks', JSON.stringify(all.slice(-500)))
    } catch (e) { /* ignore */ }
  })

// 자연어 상품 검색 (선택 10) — 조건 추출 후 상품 반환. 폴백은 챗봇과 같은 규칙 파서.
// 실제 백엔드(#32) 응답은 { extractedCondition, usedFallback, totalCount, products } 이고
// 상품 식별자는 productId 가 아니라 id 다. null 조건 필드는 표시에서 제외한다.
export const nlSearch = (message) =>
  tryApi(() => api.post('/recommendation-service/search', { query: message }, { timeout: 25000 }),
    () => mock.chat(store.getProducts(), message))
    .then((res) => {
      if (!res) return res
      const ext = res.extracted ?? res.extractedCondition
      if (ext === undefined) return res
      const cond = Object.entries(ext || {})
        .filter(([, v]) => v !== null && v !== undefined)
        .map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join('·') : v}`).join(' / ')
      return {
        reply: cond ? `추출 조건 — ${cond}` : null,
        usedFallback: res.usedFallback,
        products: (res.products || []).map((p) => ({ productId: p.productId ?? p.id, name: p.name, price: p.price })),
      }
    })

// 장바구니 서버 동기화 — 백엔드가 있으면 cart_item 저장 + cart-added 발행 트리거.
// 실패해도 화면은 로컬 장바구니로 동작한다 (베스트에포트).
export const syncCartAdd = (memberId, productId, qty) =>
  api.post('/commerce-service/carts', { memberId, productId, qty }).catch(() => {})
export const syncCartUpdate = (cartItemId, qty) =>
  api.put(`/commerce-service/carts/${cartItemId}`, { qty }).catch(() => {})
export const syncCartRemove = (cartItemId) =>
  api.delete(`/commerce-service/carts/${cartItemId}`).catch(() => {})

// 재고 동기화 현황 (CDC 파이프라인) — 관리자 동기화 탭이 폴링한다
export const fetchPosSyncStatus = () =>
  api.get('/pos-sync-service/status').then((r) => r.data).catch(() => null)

// AI 재고 소진 예측 — 소진 임박 상품 TOP N
export const fetchStockForecast = (limit = 5) =>
  api.get(`/recommendation-service/stock-forecast?limit=${limit}`).then((r) => r.data).catch(() => [])
