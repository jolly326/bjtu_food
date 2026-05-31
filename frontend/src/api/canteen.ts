import type { BannerItem, CanteenInfo, StallDetail } from '@/types/canteen'
import { get } from './http'
const MOCK_BANNERS: BannerItem[] = [
  { title: '🍜 交大美食季', subtitle: '发现校园里的每一道美味', image: '' },
  { title: '🔥 新菜品上架', subtitle: '一食堂二层新窗口开业', image: '' },
]

const MOCK_CANTEENS: CanteenInfo[] = [
  { name: '第一食堂', location: '一食堂一层', icon: '' },
  { name: '第二食堂', location: '二食堂一层', icon: '' },
  { name: '第三食堂', location: '三食堂一层', icon: '' },
]

export async function getHomeBanners(): Promise<BannerItem[]> {
  try {
    const raw: any[] = await get('/canteens/banners')
    return raw.map((b: any) => ({
      title: b.title,
      subtitle: b.subtitle || '',
      image: b.image || '',
    }))
  } catch {
    console.log('[canteen] getHomeBanners 失败，使用 mock')
    return MOCK_BANNERS
  }
}

export async function getCanteenList(): Promise<CanteenInfo[]> {
  try {
    const rawList: any[] = await get('/canteens')
    return rawList.map((c: any) => ({
      name: c.name,
      location: c.description || c.location || '',
      icon: c.icon || '',
    }))
  } catch {
    console.log('[canteen] getCanteenList 失败，使用 mock')
    return MOCK_CANTEENS
  }
}

export async function getCanteenImages(): Promise<Record<string, string>> {
  try {
    const rawMap: Record<string, string> = await get('/canteens/images')
    return Object.keys(rawMap).length > 0 ? rawMap : {}
  } catch {
    console.log('[canteen] getCanteenImages 失败，使用 mock')
    return {}
  }
}

export async function getStallDetail(canteen: string, stallName: string): Promise<StallDetail> {
  const fallback = {
    name: stallName,
    images: ['', '', ''],
    location: canteen,
    description: `${canteen}·${stallName}，为您提供美味的校园餐饮体验。精选新鲜食材，用心烹制每一道菜品。`,
  }
  try {
    const raw: any = await get('/canteens/stallDetail', { canteen, stallName })
    return {
      name: raw.name || stallName,
      images: raw.images || fallback.images,
      location: raw.location || canteen,
      description: raw.description || fallback.description,
    }
  } catch {
    console.log('[canteen] getStallDetail 失败，使用 mock')
    return fallback
  }
}
