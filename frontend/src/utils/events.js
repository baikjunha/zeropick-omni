const KEY = 'zp_events'

export function loadEvents() {
  try { return JSON.parse(localStorage.getItem(KEY) || '[]') } catch { return [] }
}

export function recordEvent(type, product, extra = {}, memberId = 1) {
  const ev = {
    type,
    memberId,
    productId: product.id,
    name: product.name,
    img: product.img || '',
    cat: product.cat,
    at: new Date().toISOString(),
    ...extra,
  }
  const all = loadEvents()
  all.push(ev)
  localStorage.setItem(KEY, JSON.stringify(all.slice(-500)))
  return ev
}

export function clearEvents(demoOnly = false) {
  if (demoOnly) {
    localStorage.setItem(KEY, JSON.stringify(loadEvents().filter((e) => !e.demo)))
  } else {
    localStorage.setItem(KEY, '[]')
  }
}
