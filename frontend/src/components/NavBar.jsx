import { useState } from 'react'
import { NavLink, useNavigate, useSearchParams } from 'react-router-dom'
import { useStore } from '../context/StoreContext.jsx'
import AuthModal from './AuthModal.jsx'

export default function NavBar() {
  const { member, setMember, cart, prefs, showToast } = useStore()
  const [auth, setAuth] = useState(null)
  const [q, setQ] = useState('')
  const nav = useNavigate()
  const [, setParams] = useSearchParams()
  const cartCount = Object.values(cart).reduce((a, b) => a + b, 0)

  const search = () => {
    nav('/')
    setParams(q ? { q } : {})
  }

  return (
    <>
      <div className="nav">
        <div className="nav-in">
          <NavLink to="/" className="logo">🧃 Zero<span className="zp">Pick</span></NavLink>
          <nav className="gnav">
            <NavLink to="/" end className={({ isActive }) => (isActive ? 'on' : '')}>제품검색</NavLink>
            <NavLink to="/cart" className={({ isActive }) => (isActive ? 'on' : '')}>
              장바구니{cartCount > 0 && ` (${cartCount})`}
            </NavLink>
            <NavLink to="/orders" className={({ isActive }) => (isActive ? 'on' : '')}>주문내역</NavLink>
          </nav>
          <div className="searchbox">
            <input
              placeholder="제품명, 브랜드로 검색해보세요"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && search()}
            />
            <button className="go" onClick={search}>🔍</button>
          </div>
          <div className="hbtns">
            <NavLink to="/profile" className="pillb" style={{ textDecoration: 'none', color: 'inherit' }}>
              👤 내 프로필 {prefs.banSw.length > 0 && <b>제외 {prefs.banSw.length}</b>}
            </NavLink>
            {member ? (
              <>
                <span style={{ fontSize: 13, color: 'var(--muted)' }}>👋 <b>{member.name}</b>님</span>
                <button className="pillb" onClick={() => { setMember(null); showToast('로그아웃했어요') }}>로그아웃</button>
              </>
            ) : (
              <>
                <button className="pillb" onClick={() => setAuth('login')}>로그인</button>
                <button className="joinb" onClick={() => setAuth('join')}>회원가입</button>
              </>
            )}
          </div>
        </div>
      </div>
      {auth && <AuthModal mode={auth} setMode={setAuth} onClose={() => setAuth(null)} />}
    </>
  )
}
