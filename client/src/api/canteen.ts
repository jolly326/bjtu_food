import type { CanteenInfo, StallDetail } from '@/types/canteen'
import type { BannerItem } from '@/types/banner'
import { get } from './http'
import { normalizeImages } from './_shared'

function firstImage(raw: any): string {
  return normalizeImages(raw?.images ?? raw?.image ?? raw?.icon)[0] || ''
}

export async function getHomeBanners(): Promise<BannerItem[]> {
  const raw = await get<any[]>('/canteens/banners')
  return raw.map((b: any) => ({
    id: Number(b.id),
    title: b.title || '',
    subtitle: b.subtitle || '',
    image: firstImage(b),
    targetType: (b.targetType || b.target_type || 'NONE') as BannerItem['targetType'],
    targetId: b.targetId != null ? Number(b.targetId) : (b.target_id != null ? Number(b.target_id) : undefined),
    targetUrl: b.targetUrl || b.target_url || '',
  }))
}

export async function getCanteenList(lat?: number | null, lng?: number | null): Promise<CanteenInfo[]> {
  const params: Record<string, unknown> = {}
  if (typeof lat === 'number' && typeof lng === 'number') {
    params.lat = lat
    params.lng = lng
  }
  const rawList = await get<any[]>('/canteens', params)
  return rawList.map((c: any) => ({
    name: c.name || '',
    location: c.location || c.description || '',
    icon: firstImage(c),
    distance: c.distance != null ? Number(c.distance) : undefined,
  }))
}

export async function getCanteenImages(): Promise<Record<string, string>> {
  const rawMap = await get<Record<string, unknown>>('/canteens/images')
  return Object.fromEntries(
    Object.entries(rawMap || {}).map(([name, value]) => [name, normalizeImages(value)[0] || '']),
  )
}

export async function getStallDetail(canteen: string, stallName: string): Promise<StallDetail> {
  const raw = await get<any>('/canteens/stallDetail', { canteen, canteenName: canteen, stallName })
  return {
    id: raw.id != null ? Number(raw.id) : undefined,
    name: raw.name || stallName,
    images: normalizeImages(raw.images ?? raw.image),
    location: raw.location || canteen,
    description: raw.description || '',
    avgRating: raw.avgRating != null ? Number(raw.avgRating) : undefined,
    tags: Array.isArray(raw.tags) ? raw.tags.filter((t: any): t is string => typeof t === 'string') : undefined,
  }
}

export async function getCanteensWithStalls(): Promise<any[]> {
  return await get<any[]>('/canteens/all')
}
