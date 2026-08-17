import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore } from '../context/StoreContext.jsx'
import * as api from '../api.js'

const METHODS = ['카드', '간편결제', '무통장입금']

export default function CheckoutModal({ items, onClose, clearCartAfter = false }) {
  const { member, emit, setCart, showToast } = useStore()
  const [method, setMethod] = useState('카드')
  const [paying, setPaying] = useState(false)
  const nav = useNavigate()
  const total = items.reduce((a, x) => a + x.product.price * x.qty, 0)

  const pay = async () => {
    setPaying(true)
    try {

      const order = await api.createOrder({
        memberId: member?.memberId || 1,
        items: items.map((x) => ({ productId: x.product.id, name: x.product.name, qty: x.qty, unitPrice: x.product.price })),
        totalPrice: total,
      })
      await api.payOrder(order.orderId, { paymentMethod: method })
      items.forEach((x) => emit('ORDER_COMPLETED', x.product, { qty: x.qty, payment: method }))
      if (clearCartAfter) setCart({})
      showToast(`✅ ${method} 결제 완료 — ${order.orderNo}`)
      onClose()
      nav('/orders')
    } catch (e) {
      showToast('⚠️ 결제에 실패했어요 (재고 부족 여부를 확인해 주세요)')
      setPaying(false)
    }
  }

  return (
    <div className="modalbg show" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal sm">
        <button className="x" onClick={onClose}>✕</button>
        <h3>주문 · 결제</h3>
        <p className="sub2">모의 결제 — 결제 완료 시점에 재고가 차감돼요</p>
        {items.map((x) => (
          <div key={x.product.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 240 }}>
              {x.product.name} × {x.qty}
            </span>
            <b className="num">{(x.product.price * x.qty).toLocaleString()}원</b>
          </div>
        ))}
        <div style={{ borderTop: '1px solid var(--line)', margin: '10px 0', paddingTop: 10,
                      display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
          <span>합계</span><b className="num" style={{ fontSize: 17 }}>{total.toLocaleString()}원</b>
        </div>
        <div className="mh5">결제수단</div>
        <div className="chips">
          {METHODS.map((m) => (
            <button key={m} className={`chip ${method === m ? 'on' : ''}`} onClick={() => setMethod(m)}>{m}</button>
          ))}
        </div>
        <button className="primaryb" style={{ marginTop: 18 }} disabled={paying} onClick={pay}>
          {paying ? '결제 중…' : `${total.toLocaleString()}원 결제하기`}
        </button>
      </div>
    </div>
  )
}
