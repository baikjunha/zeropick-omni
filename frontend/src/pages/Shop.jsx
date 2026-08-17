import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useStore } from '../context/StoreContext.jsx'
import { getProducts } from '../utils/productStore.js'
import { scoreProducts } from '../utils/mock.js'
import * as api from '../api.js'

const NL_PATTERN = /없|빼|제외|이하|이상|미만|안 ?들어|아래/
import FilterSidebar from '../components/FilterSidebar.jsx'
import ProductCard from '../components/ProductCard.jsx'
import DetailModal from '../components/DetailModal.jsx'
import CompareTray from '../components/CompareTray.jsx'
import CheckoutModal from '../components/CheckoutModal.jsx'

export default function Shop() {
  const { prefs, member } = useStore()
  const [params] = useSearchParams()
  const q = params.get('q') || ''
  const [filters, setFilters] = useState({ category: null, minKcal: 0, maxKcal: 999, minPrice: 0, maxPrice: 999999 })
  const [sort, setSort] = useState('reco')
  const [products, setProducts] = useState(getProducts)
  const [detail, setDetail] = useState(() => {
    const pid = new URLSearchParams(window.location.search).get('product')
    return pid ? getProducts().find((x) => x.id === Number(pid)) || null : null
  })
  const [buying, setBuying] = useState(null)

  const [nlInfo, setNlInfo] = useState(null)
  useEffect(() => {
    let alive = true
    if (q && NL_PATTERN.test(q)) {

      api.nlSearch(q).then((res) => {
        if (!alive || !res) return
        const ids = (res.products || []).map((x) => x.productId)
        setNlInfo(res.reply || null)
        setProducts(getProducts().filter((p) => ids.includes(p.id)))
      }).catch(() => {})
      return () => { alive = false }
    }
    setNlInfo(null)
    api.fetchProducts({
      category: filters.category || undefined,
      sweetenerExclude: prefs.banSw.join(',') || undefined,
      kcalMin: filters.minKcal || undefined,
      kcalMax: filters.maxKcal < 999 ? filters.maxKcal : undefined,
      q: q || undefined,
    }).then((data) => { if (alive && data) setProducts(Array.isArray(data) ? data : data?.content || []) }).catch(() => {})
    return () => { alive = false }
  }, [filters, prefs.banSw, q])

  const [serverReco, setServerReco] = useState(null)
  useEffect(() => {
    let alive = true
    api.fetchRecommendations(member?.memberId || 1).then((list) => {
      if (alive && Array.isArray(list) && list.length && list[0].productId !== undefined) {
        setServerReco(list.map((ri) => [ri.productId, { score: ri.score }]))
      }
    }).catch(() => {})
    return () => { alive = false }
  }, [member])

  const scored = useMemo(() => {
    const s = scoreProducts(products, prefs)
    const local = new Map(s.map((x, i) => [x.product.id, { rank: i, score: x.score }]))
    if (!serverReco || !serverReco.length) return local
    const merged = new Map()
    let i = 0
    for (const [id, v] of serverReco) merged.set(id, { rank: i++, score: Math.max(v.score, 2) })
    for (const [id, v] of local) if (!merged.has(id)) merged.set(id, { rank: i + v.rank, score: v.score })
    return merged
  }, [products, prefs, serverReco])
  const recoTop = useMemo(() => {
    const ids = [...scored.entries()].filter(([, v]) => v.score > 1.5).sort((a, b) => a[1].rank - b[1].rank)
    return new Set(ids.slice(0, 8).map(([id]) => id))
  }, [scored])

  const priced = useMemo(() => products.filter((p) =>
    p.price >= (Number(filters.minPrice) || 0) && p.price <= (Number(filters.maxPrice) || 999999)
  ), [products, filters.minPrice, filters.maxPrice])

  const sorted = useMemo(() => {
    const r = [...priced]
    if (sort === 'reco') r.sort((a, b) => (scored.get(a.id)?.rank ?? 1e9) - (scored.get(b.id)?.rank ?? 1e9))
    if (sort === 'priceAsc') r.sort((a, b) => a.price - b.price)
    if (sort === 'priceDesc') r.sort((a, b) => b.price - a.price)
    if (sort === 'kcal') r.sort((a, b) => a.kcal - b.kcal)
    return r
  }, [priced, sort, scored])

  return (
    <div className="wrap">
      <FilterSidebar filters={filters} setFilters={setFilters} />
      <main className="content">
        <div className="toolbar">
          <h2>제품검색</h2>
          <span className="cnt num">총 {sorted.length}개 제품{q && ` · "${q}" 검색`}</span>
          <select value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="reco">추천순 ✨</option>
            <option value="priceAsc">낮은 가격순</option>
            <option value="priceDesc">높은 가격순</option>
            <option value="kcal">낮은 칼로리순</option>
          </select>
        </div>
        {nlInfo && <div className="hint" style={{ background: 'var(--primary-soft)', borderRadius: 10, padding: '8px 12px' }}>
          🔎 자연어 검색 — {nlInfo}</div>}
        <div className="hint">
          기본 정렬은 <b>추천순</b> — {member ? `${member.name}님의` : '회원님의'} 행동·조건이 반영돼요 ·
          카드의 <b>+비교</b>로 원하는 만큼 나란히 볼 수 있어요
          {prefs.banSw.length > 0 && <> · 🚫 제외 감미료 조건이 적용 중이에요</>}
        </div>
        <div className="grid">
          {sorted.map((p) => (
            <ProductCard key={p.id} p={p} reco={recoTop.has(p.id)}
              onDetail={(prod) => {

                if (recoTop.has(prod.id)) api.postRecoClick({ memberId: member?.memberId || 1, productId: prod.id })
                setDetail(prod)
              }}
              onBuy={setBuying} />
          ))}
        </div>
        {sorted.length === 0 && <div className="empty">조건에 맞는 상품이 없어요 — 필터를 풀어보세요</div>}
      </main>
      {detail && <DetailModal p={detail} onClose={() => setDetail(null)} onBuy={(p) => { setDetail(null); setBuying(p) }} />}
      {buying && <CheckoutModal items={[{ product: buying, qty: 1 }]} onClose={() => setBuying(null)} />}
      <CompareTray />
    </div>
  )
}
