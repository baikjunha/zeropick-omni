import { useState, useRef, useEffect } from 'react'
import { useStore } from '../context/StoreContext.jsx'
import * as api from '../api.js'
import DetailModal from './DetailModal.jsx'
import CheckoutModal from './CheckoutModal.jsx'

const SUGGESTS = ['말티톨 없는 제로 초콜릿', '아스파탐 안 들어간 콜라', '1만원 이하 저당 아이스크림']

export default function ChatWidget() {
  const { member } = useStore()
  const [open, setOpen] = useState(false)
  const [msgs, setMsgs] = useState([
    { role: 'bot', text: `안녕하세요${member ? `, ${member.name}님` : ''}! 제로픽 상담봇이에요. 원하는 조건을 자연어로 말해 주세요.` },
  ])
  const [input, setInput] = useState('')
  const [detail, setDetail] = useState(null)
  const [buying, setBuying] = useState(null)
  const endRef = useRef(null)

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [msgs])

  const send = async (text) => {
    const message = (text || input).trim()
    if (!message) return
    setInput('')
    setMsgs((m) => [...m, { role: 'user', text: message }])
    const res = await api.chat({ memberId: member?.memberId || 1, message })
    setMsgs((m) => [...m, { role: 'bot', text: res.reply, products: res.products, usedFallback: res.usedFallback }])
  }

  const openDetail = async (p) => {
    const full = await api.fetchProduct(p.productId).catch(() => null)
    setDetail(full || { id: p.productId, name: p.name, price: p.price, brand: '', cat: '', stock: 0, kcal: 0, sugar: 0, sw: [] })
  }

  return (
    <>
      <button className="chatfab" onClick={() => setOpen((o) => !o)}>{open ? '✕' : '💬'}</button>
      <div className={`chatpanel ${open ? 'show' : ''}`}>
        <div className="chathead">
          <b>제로픽 상담봇</b>
          <small>조건 추출 → 하드필터 → 랭킹 — LLM 장애 시 규칙 기반으로 답해요</small>
        </div>
        <div className="chatmsgs">
          {msgs.map((m, i) => (
            <div key={i} className={`cmsg ${m.role}`}>
              {m.text}
              {m.products?.map((p) => (
                <div key={p.productId} className="mini" onClick={() => openDetail(p)}>
                  <span style={{ flex: 1 }}>{p.name}</span>
                  <b className="num">{(p.price ?? 0).toLocaleString()}원</b>
                </div>
              ))}
              {m.usedFallback && <span className="fb">규칙 기반 응답 (usedFallback)</span>}
            </div>
          ))}
          <div ref={endRef} />
        </div>
        <div className="chatchips">
          {SUGGESTS.map((s) => <button key={s} onClick={() => send(s)}>{s}</button>)}
        </div>
        <div className="chatin">
          <input value={input} placeholder="예: 수크랄로스 없는 음료 2만원 이하"
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send()} />
          <button onClick={() => send()}>전송</button>
        </div>
      </div>
      {detail && <DetailModal p={detail} onClose={() => setDetail(null)} onBuy={(p) => { setDetail(null); setBuying(p) }} />}
      {buying && <CheckoutModal items={[{ product: buying, qty: 1 }]} onClose={() => setBuying(null)} />}
    </>
  )
}
