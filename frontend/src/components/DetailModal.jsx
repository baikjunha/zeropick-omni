import { useEffect, useState } from 'react'
import { useStore } from '../context/StoreContext.jsx'

const NUT_ROWS = [
  ['열량', 'kcal', ' kcal'],
  ['당류', 'sugar', ' g'],
  ['탄수화물', 'carb', ' g'],
  ['단백질', 'protein', ' g'],
  ['지방', 'fat', ' g'],
  ['나트륨', 'sodium', ' mg'],
]

export default function DetailModal({ p, onClose, onBuy }) {
  const { addCart, emit, prefs } = useStore()
  const [showNutri, setShowNutri] = useState(false)
  const conflicts = p.sw.filter((s) => prefs.banSw.includes(s))

  useEffect(() => { emit('PRODUCT_VIEWED', p) }, [p, emit])

  return (
    <div className="modalbg show" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: 760 }}>
        <button className="x" onClick={onClose}>✕</button>
        <div className="dm-grid">
          {}
          <div>
            <div className="dm-photo">
              {p.e}
              {p.img && <img src={p.img} alt="" onError={(e) => e.target.remove()} />}
            </div>
            {p.nutriImg && (
              <button className="dm-nutribtn" onClick={() => setShowNutri((v) => !v)}>
                {showNutri ? '영양성분표 접기 ▲' : '📋 영양성분표 원본 보기 ▼'}
              </button>
            )}
          </div>

          {}
          <div className="dm-info">
            <div className="dm-meta">{p.brand} · {p.cat}</div>
            <h3 className="dm-name">{p.name}</h3>
            <div className="dm-pricerow">
              <span className="dm-price num">{p.price.toLocaleString()}<small> KRW</small></span>
              <span className={`stock num ${p.stock > 0 && p.stock < 10 ? 'low' : ''}`}>
                {p.stock > 0 ? `재고 ${p.stock}개` : '품절'}
              </span>
            </div>

            <div className="dm-sect">감미료 구성</div>
            <div className="badges">
              {p.sw.length === 0 && <span className="bdg none">감미료 무첨가</span>}
              {(p.swd || p.sw.map((n) => ({ n }))).map((s) => (
                <span key={s.n} className={`bdg ${prefs.banSw.includes(s.n) ? 'conflict' : ''}`}>
                  {s.n}{s.g != null && s.g > 0 && <em className="num"> {s.g}g</em>}
                </span>
              ))}
            </div>
            {conflicts.length > 0 && (
              <div className="dm-warn">⚠️ 프로필에서 제외한 감미료({conflicts.join(', ')})가 들어 있어요</div>
            )}

            <div className="dm-sect">
              영양성분
              {p.serving > 0 && <small> — 1회 제공량 {p.serving}{p.servingUnit} 기준</small>}
            </div>
            <div className="dm-nutgrid">
              {NUT_ROWS.map(([label, key, unit]) => (
                <div key={key} className="dm-nutcell">
                  <span>{label}</span>
                  <b className="num">{p[key] != null && p[key] !== 0 || key === 'sugar' || key === 'kcal'
                    ? `${p[key]}${unit}` : '—'}</b>
                </div>
              ))}
            </div>

            <div className="dm-acts">
              <button className="pillb" onClick={() => { addCart(p); onClose() }}>🛒 장바구니 담기</button>
              <button className="primaryb" style={{ width: 'auto', flex: 1 }} onClick={() => onBuy(p)}>바로 주문</button>
            </div>
          </div>
        </div>

        {}
        {showNutri && p.nutriImg && (
          <div className="dm-nutriimg">
            <img src={p.nutriImg} alt="영양성분표" onError={(e) => { e.target.parentNode.textContent = '성분표 이미지를 불러오지 못했어요' }} />
            <div className="dm-src">식품 표시정보 원본 — 제품 포장 기준</div>
          </div>
        )}
      </div>
    </div>
  )
}
