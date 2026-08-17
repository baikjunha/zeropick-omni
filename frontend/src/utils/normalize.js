const CAT_EMOJI = {
  '음료': '🥤', '간식/디저트': '🍫', '육가공품': '🍖', '조미료/소스': '🧂', '유제품': '🥛',
  '주식/면류': '🍜', '즉석식품': '🍱', '건강기능식품': '💊', '수산가공품': '🐟', '기타': '🛒',
}

export function toViewProduct(raw) {
  if (!raw) return raw
  if (raw.cat !== undefined) return raw
  return {
    id: raw.id,
    name: raw.name,
    brand: raw.brand || '',
    cat: raw.category || '기타',
    e: CAT_EMOJI[raw.category] || '🛒',
    kcal: raw.kcal ?? 0,
    sugar: raw.sugarG ?? 0,
    carb: raw.carbG ?? 0,
    protein: raw.proteinG ?? 0,
    fat: raw.fatG ?? 0,
    sodium: raw.sodiumMg ?? 0,
    serving: raw.servingSize ?? 0,
    servingUnit: raw.servingUnit || 'g',
    sw: raw.sweeteners || [],
    swd: (raw.sweetenerAmounts || []).map((a) => ({ n: a.name, g: a.amountG })),
    price: raw.price ?? 0,
    stock: raw.stock ?? 0,
    img: raw.imageUrl || '',
    nutriImg: raw.nutritionFactsUrl || '',
  }
}

export const toViewProducts = (list) => (Array.isArray(list) ? list.map(toViewProduct) : list)

export function toApiProduct(p) {
  return {
    name: p.name,
    brand: p.brand || '기타',
    category: p.cat || p.category || '기타',
    price: Number(p.price) || 0,
    stock: Number(p.stock) || 0,
    kcal: Number(p.kcal) || 0,
    sugarG: Number(p.sugar ?? p.sugarG) || 0,
    carbG: Number(p.carb ?? p.carbG) || 0,
    imageUrl: p.img || p.imageUrl || undefined,
    sweeteners: p.sw || p.sweeteners || [],
  }
}

export function toApiPreference(memberId, prefs) {
  return {
    memberId,
    priceMin: Number(prefs.priceMin) || 0,
    priceMax: Number(prefs.priceMax) || 100000,
    categories: prefs.cats || [],
    excludedSweeteners: prefs.banSw || [],
    allergens: prefs.banAllergen || [],
  }
}

export function toViewPreference(raw) {
  if (!raw) return null
  return {
    banSw: raw.excludedSweeteners || [],
    banAllergen: raw.allergens || [],
    cats: raw.categories || [],
    priceMin: raw.priceMin ?? 0,
    priceMax: raw.priceMax ?? 100000,
  }
}
