import axios from 'axios'
import { seedProducts } from './data/seed.js'
import * as store from './utils/productStore.js'
import * as mock from './utils/mock.js'
import { toViewProduct, toViewProducts, toApiProduct, toApiPreference } from './utils/normalize.js'

const productCache = new Map()
function cacheProducts(list) {
  if (Array.isArray(list)) list.forEach((p) => productCache.set(p.id, { name: p.name, price: p.price }))
  return list
}
function lookupProduct(id) {
  return productCache.get(id) || store.getProducts().find((p) => p.id === id)
    || { name: `상품 ${id}`, price: 0 }
}

const api = axios.create({
  headers: { 'Content-Type': 'application/json' },
  timeout: 4000,
})

api.interceptors.request.use((config) => {
  const member = JSON.parse(sessionStorage.getItem('zp_member') || 'null')
  if (member?.token) config.headers.Authorization = `Bearer ${member.token}`
  return config
})

let mockMode = false
const mockListeners = new Set()
export const isMock = () => mockMode
export const onMockChange = (fn) => { mockListeners.add(fn); return () => mockListeners.delete(fn) }
function setMock(v = true) {
  if (mockMode !== v) { mockMode = v; mockListeners.forEach((fn) => fn(v)) }
}

function isJsonResponse(res) {
  const ct = String(res.headers?.['content-type'] || '')
  return ct.includes('application/json') || (typeof res.data === 'object' && res.data !== null)
}

async function tryApi(call, fallback) {
  try {
    const res = await call()
    if (!isJsonResponse(res)) { setMock(true); return fallback() }
    setMock(false)
    return res.data
  } catch (e) {
    if (!e.response || e.response.status >= 500) {
      setMock(true)
      return fallback()
    }
    throw e
  }
}

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

export const postBehavior = (ev) =>
  api.post('/commerce-service/behaviors', {
    memberId: ev.memberId,
    productId: ev.productId,
    eventType: ev.type,
    category: ev.cat,
    occurredAt: ev.at,
  }).catch(() => {})

export const savePreferences = (memberId, prefs) =>
  tryApi(() => api.post('/recommendation-service/preferences', toApiPreference(memberId, prefs)), () => prefs)

export const fetchRecommendations = (memberId) =>
  tryApi(() => api.get(`/recommendation-service/recommendations/${memberId}`),
    () => mock.recommend(store.getProducts()))

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

export const fetchMetrics = () =>
  tryApi(() => api.get('/recommendation-service/metrics'), () => mock.metrics())
    .then((m) => {
      if (!m) return m
      if (m.clickThroughRate !== undefined) {
        const local = mock.metrics()
        return {
          viewed: local.viewed, cartAdded: local.cartAdded, ordered: local.ordered,
          conversionRate: local.conversionRate,
          recoClicks: m.totalClicks ?? 0,
          clickRate: m.clickThroughRate ?? 0,
          fallbackRate: m.fallbackRate ?? 0,
          chatCount: m.totalChatRequests ?? 0,
          avgLatencyMs: null, chatAvgMs: null, chatP95Ms: null,
        }
      }
      if (m.ctr === undefined) return m
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

export const postRecoClick = (body) =>
  api.post('/recommendation-service/click', body).catch(() => {
    try {
      const all = JSON.parse(localStorage.getItem('zp_reco_clicks') || '[]')
      all.push({ ...body, at: new Date().toISOString() })
      localStorage.setItem('zp_reco_clicks', JSON.stringify(all.slice(-500)))
    } catch (e) {  }
  })

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

export const syncCartAdd = (memberId, productId, qty) =>
  api.post('/commerce-service/carts', { memberId, productId, qty }).catch(() => {})
export const syncCartUpdate = (cartItemId, qty) =>
  api.put(`/commerce-service/carts/${cartItemId}`, { qty }).catch(() => {})
export const syncCartRemove = (cartItemId) =>
  api.delete(`/commerce-service/carts/${cartItemId}`).catch(() => {})

export const fetchPosSyncStatus = () =>
  api.get('/pos-sync-service/status').then((r) => r.data).catch(() => null)

export const fetchStocks = (ids) =>
  api.get('/stock-service/stocks', { params: { ids: ids.join(',') } }).then((r) => r.data).catch(() => [])

export const syncStockLedger = () =>
  api.post('/stock-service/stocks/sync').then((r) => r.data).catch(() => null)

export const fetchStockForecast = (limit = 5) =>
  api.get(`/recommendation-service/stock-forecast?limit=${limit}`).then((r) => r.data).catch(() => [])
