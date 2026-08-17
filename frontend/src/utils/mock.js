import { loadEvents } from './events.js'
import { updateProduct, getProducts } from './productStore.js'

const W = { PRODUCT_VIEWED: 1, CART_ADDED: 0, ORDER_COMPLETED: 50 }

function behaviorScores() {
  const score = {}
  for (const e of loadEvents()) {
    score[e.productId] = (score[e.productId] || 0) + (W[e.type] || 0)
  }
  return score
}

export function scoreProducts(products, prefs) {
  const beh = behaviorScores()
  const banSw = prefs?.banSw || []
  const out = []
  for (const p of products) {
    if (p.sw.some((s) => banSw.includes(s))) continue
    let s = beh[p.id] || 0
    const why = []
    if (beh[p.id]) why.push('행동 기반 +' + beh[p.id])
    if (p.sugar === 0) { s += 1.5; why.push('당류 0g') }
    if (prefs?.cats?.includes(p.cat)) { s += 2; why.push('선호 카테고리') }
    if (prefs && p.price >= (prefs.priceMin || 0) && p.price <= (prefs.priceMax || 1e9)) s += 0.5
    out.push({ product: p, score: s, why })
  }
  return out.sort((a, b) => b.score - a.score || a.product.id - b.product.id)
}

export function filterProducts(products, params = {}) {
  let r = [...products]
  if (params.category) r = r.filter((p) => p.cat === params.category)
  if (params.sweetenerExclude) {
    const ban = String(params.sweetenerExclude).split(',')
    r = r.filter((p) => !p.sw.some((s) => ban.includes(s)))
  }
  if (params.sugarMax != null) r = r.filter((p) => p.sugar <= params.sugarMax)
  if (params.kcalMin != null) r = r.filter((p) => p.kcal >= params.kcalMin)
  if (params.kcalMax != null) r = r.filter((p) => p.kcal <= params.kcalMax)
  if (params.q) {
    const q = String(params.q).toLowerCase()
    r = r.filter((p) => p.name.toLowerCase().includes(q) || p.brand.toLowerCase().includes(q))
  }
  return r
}

export function recommend(products) {
  const prefs = JSON.parse(localStorage.getItem('zp_prefs') || '{}')
  return scoreProducts(products, prefs).slice(0, 50)
    .map((x, i) => ({ productId: x.product.id, rank: i + 1, score: x.score, reason: x.why.join(' · ') }))
}

function members() { return JSON.parse(localStorage.getItem('zp_members') || '[]') }
export function join({ name, email, password }) {
  const all = members()
  if (all.some((m) => m.email === email)) {
    const err = new Error('duplicate'); err.status = 409; throw err
  }
  const m = { memberId: all.length + 1, name, email, password }
  all.push(m)
  localStorage.setItem('zp_members', JSON.stringify(all))
  return { memberId: m.memberId, name: m.name }
}
export function login({ email, password }) {
  const m = members().find((x) => x.email === email && x.password === password)
  if (!m) { const err = new Error('unauthorized'); err.status = 401; throw err }
  return { memberId: m.memberId, name: m.name }
}

function orders() { return JSON.parse(localStorage.getItem('zp_orders') || '[]') }
export function createOrder(body) {
  const all = orders()
  const order = {
    orderId: all.length + 1,
    orderNo: 'ZP' + String(1000 + all.length + 1),
    status: 'PENDING',
    items: body.items, totalPrice: body.totalPrice,
    orderedAt: new Date().toISOString(),
  }
  all.push(order)
  localStorage.setItem('zp_orders', JSON.stringify(all))
  return order
}
export function payOrder(orderId, { paymentMethod }) {
  const all = orders()
  const o = all.find((x) => x.orderId === Number(orderId))
  if (o) {

    const products = getProducts()
    for (const it of o.items || []) {
      const prod = products.find((x) => x.id === it.productId)
      if (prod && prod.stock < (it.qty || 1)) {
        o.status = 'CANCELLED'
        localStorage.setItem('zp_orders', JSON.stringify(all))
        const err = new Error('out of stock'); err.status = 409; throw err
      }
    }
    for (const it of o.items || []) {
      const prod = products.find((x) => x.id === it.productId)
      if (prod) updateProduct(prod.id, { stock: prod.stock - (it.qty || 1) })
    }
    o.status = 'PAID'; o.paymentMethod = paymentMethod; o.paidAt = new Date().toISOString()
  }
  localStorage.setItem('zp_orders', JSON.stringify(all))
  return o
}
export function cancelOrder(orderId) {
  const all = orders()
  const o = all.find((x) => x.orderId === Number(orderId))
  if (o && o.status !== 'CANCELLED') {

    if (o.status === 'PAID') {
      const products = getProducts()
      for (const it of o.items || []) {
        const prod = products.find((x) => x.id === it.productId)
        if (prod) updateProduct(prod.id, { stock: prod.stock + (it.qty || 1) })
      }
    }
    o.status = 'CANCELLED'; o.cancelledAt = new Date().toISOString()
  }
  localStorage.setItem('zp_orders', JSON.stringify(all))
  return o
}
export function fetchOrders() { return orders().slice().reverse() }

const SWEETENERS = ['D-말티톨','D-소비톨액','나한과추출분말','말티톨','수크랄로스','스테비아','스테비올 배당체',
  '아라비아검','아세설팜칼륨','아스파탐','알룰로스','에리스리톨','이소말트','자일리톨','효소처리스테비아']

export function chat(products, message) {
  const cond = { ban: [], cat: null, maxPrice: null, q: [] }
  for (const s of SWEETENERS) {
    const base = s.replace('D-', '')
    if (message.includes(base) && /없|빼|제외|안 ?들어/.test(message)) cond.ban.push(s)
  }
  const priceM = message.match(/([\d,만천]+)\s*원?\s*(이하|아래|밑|미만)/)
  if (priceM) {
    let n = priceM[1].replace(/,/g, '')
    n = n.replace('만', '0000').replace('천', '000')
    cond.maxPrice = parseInt(n, 10) || null
  }
  const catMap = { 초콜릿: '간식/디저트', 과자: '간식/디저트', 아이스크림: '간식/디저트', 젤리: '간식/디저트',
    음료: '음료', 콜라: '음료', 사이다: '음료', 요거트: '유제품', 우유: '유제품', 소스: '조미료/소스', 라면: '주식/면류' }
  for (const [k, v] of Object.entries(catMap)) if (message.includes(k)) { cond.cat = v; cond.q.push(k); break }

  let r = products.filter((p) => !p.sw.some((s) => cond.ban.includes(s)))
  if (cond.cat) r = r.filter((p) => p.cat === cond.cat || cond.q.some((q) => p.name.includes(q)))
  if (cond.maxPrice) r = r.filter((p) => p.price <= cond.maxPrice)
  const prefs = JSON.parse(localStorage.getItem('zp_prefs') || '{}')
  const ranked = scoreProducts(r, prefs).slice(0, 3)

  const condTxt = [
    cond.ban.length ? `${cond.ban.map((b) => b.replace('D-', '')).join('·')} 제외` : null,
    cond.cat ? cond.cat : null,
    cond.maxPrice ? `${cond.maxPrice.toLocaleString()}원 이하` : null,
  ].filter(Boolean).join(' · ') || '전체 상품'

  return {
    reply: ranked.length
      ? `조건(${condTxt})에 맞는 상품 ${ranked.length}개를 찾았어요.`
      : `조건(${condTxt})에 맞는 상품이 없어요. 조건을 조금 풀어볼까요?`,
    products: ranked.map((x) => ({ productId: x.product.id, name: x.product.name, price: x.product.price, reason: x.why.join(' · ') })),
    usedFallback: true,
  }
}

export function metrics() {
  const ev = loadEvents()
  const n = (t) => ev.filter((e) => e.type === t).length
  const v = n('PRODUCT_VIEWED'), c = n('CART_ADDED'), o = n('ORDER_COMPLETED')
  let clicks = 0
  try { clicks = JSON.parse(localStorage.getItem('zp_reco_clicks') || '[]').length } catch {  }
  return {
    viewed: v, cartAdded: c, ordered: o,
    conversionRate: v ? Math.round((o / v) * 1000) / 10 : 0,
    recoClicks: clicks,
    clickRate: v ? Math.round((clicks / v) * 1000) / 10 : 0,
    fallbackRate: 100,
    events: ev,
  }
}
