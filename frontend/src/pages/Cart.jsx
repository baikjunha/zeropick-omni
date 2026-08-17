import { useState } from 'react'
import { useStore } from '../context/StoreContext.jsx'
import * as api from '../api.js'
import { getProducts } from '../utils/productStore.js'
import CheckoutModal from '../components/CheckoutModal.jsx'

export default function Cart() {
  const { cart, setCart } = useStore()
  const [checkout, setCheckout] = useState(false)
  const items = Object.entries(cart)
    .map(([id, qty]) => ({ product: getProducts().find((p) => p.id === Number(id)), qty }))
    .filter((x) => x.product)
  const total = items.reduce((a, x) => a + x.product.price * x.qty, 0)

  const setQty = (id, qty) => {
    if (qty <= 0) {
      const { [id]: _, ...rest } = cart
      setCart(rest)
      api.syncCartRemove(id)
    } else {
      setCart({ ...cart, [id]: qty })
      api.syncCartUpdate(id, qty)
    }
  }

  return (
    <div className="pagewrap">
      <h2>장바구니</h2>
      <p className="sub num">{items.length}개 상품</p>
      {items.length === 0 && <div className="empty">장바구니가 비어 있어요</div>}
      {items.map(({ product: p, qty }) => (
        <div key={p.id} className="lrow">
          <div className="lthumb">{p.e}{p.img && <img src={p.img} alt="" onError={(e) => e.target.remove()} />}</div>
          <div className="nm"><b>{p.name}</b><span>{p.brand} · {p.price.toLocaleString()}원</span></div>
          <div className="qty">
            <button onClick={() => setQty(p.id, qty - 1)}>−</button>
            <b className="num">{qty}</b>
            <button onClick={() => setQty(p.id, qty + 1)}>+</button>
          </div>
          <div className="rp num">{(p.price * qty).toLocaleString()}원</div>
          <button className="delb" onClick={() => setQty(p.id, 0)}>삭제</button>
        </div>
      ))}
      {items.length > 0 && (
        <div className="orderbar">
          <span className="tt">합계<b className="num">{total.toLocaleString()}원</b></span>
          <button onClick={() => setCheckout(true)}>주문하기</button>
        </div>
      )}
      {checkout && <CheckoutModal items={items} clearCartAfter onClose={() => setCheckout(false)} />}
    </div>
  )
}
