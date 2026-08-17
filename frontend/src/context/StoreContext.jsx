import { createContext, useContext, useState, useCallback, useEffect } from 'react'
import * as api from '../api.js'
import { recordEvent } from '../utils/events.js'

const StoreContext = createContext(null)
export const useStore = () => useContext(StoreContext)

export function StoreProvider({ children }) {
  const [member, setMember] = useState(() => JSON.parse(sessionStorage.getItem('zp_member') || 'null'))
  const [prefs, setPrefsState] = useState(() => JSON.parse(localStorage.getItem('zp_prefs') ||
    '{"banSw":[],"banAllergen":[],"cats":[],"priceMin":0,"priceMax":100000}'))
  const [cart, setCart] = useState(() => JSON.parse(localStorage.getItem('zp_cart') || '{}'))
  const [compare, setCompare] = useState([])
  const [toast, setToast] = useState(null)
  const [mock, setMockState] = useState(api.isMock())

  useEffect(() => api.onMockChange(setMockState), [])
  useEffect(() => { localStorage.setItem('zp_cart', JSON.stringify(cart)) }, [cart])
  useEffect(() => { localStorage.setItem('zp_prefs', JSON.stringify(prefs)) }, [prefs])
  useEffect(() => { sessionStorage.setItem('zp_member', JSON.stringify(member)) }, [member])

  const showToast = useCallback((msg) => {
    setToast(msg)
    setTimeout(() => setToast(null), 2200)
  }, [])

  const emit = useCallback((type, product, extra = {}) => {
    const ev = recordEvent(type, product, extra, member?.memberId || 1)
    if (type === 'PRODUCT_VIEWED') api.postBehavior(ev)
  }, [member])

  const addCart = useCallback((product, qty = 1) => {
    setCart((c) => ({ ...c, [product.id]: (c[product.id] || 0) + qty }))
    emit('CART_ADDED', product, { qty })
    api.syncCartAdd(member?.memberId || 1, product.id, qty)
    showToast(`🛒 ${product.name.slice(0, 18)} 담았어요`)
  }, [emit, showToast, member])

  const setPrefs = useCallback(async (next) => {
    setPrefsState(next)
    if (member) await api.savePreferences(member.memberId, next)
  }, [member])

  const toggleCompare = useCallback((id) => {
    setCompare((c) => c.includes(id) ? c.filter((x) => x !== id) : [...c, id])
  }, [])

  const value = {
    member, setMember,
    prefs, setPrefs,
    cart, setCart, addCart,
    compare, setCompare, toggleCompare,
    toast, showToast,
    emit, mock,
  }
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>
}
