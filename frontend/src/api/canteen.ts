import type { CanteenInfo, StallDetail } from '@/types/canteen'
import type { BannerItem } from '@/types/banner'
import { get } from './http'
import { getImageUrl } from '@/utils/image'

export function normalizeImages(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string' && item.length > 0).map(getImageUrl)
  if (typeof value !== 'string' || !value.trim()) return []
  const text = value.trim()
  try {
    const parsed = JSON.parse(text)
    return Array.isArray(parsed) ? normalizeImages(parsed) : [getImageUrl(text)]
  } catch {
    return text.split('|||').map(item => item.trim()).filter(Boolean).map(getImageUrl)
  }
}

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

export async function getCanteenList(): Promise<CanteenInfo[]> {
  const rawList = await get<any[]>('/canteens')
  return rawList.map((c: any) => ({
    name: c.name || '',
    location: c.location || c.description || '',
    icon: firstImage(c),
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
  }
}

export async function getCanteensWithStalls(): Promise<any[]> {
  return await get<any[]>('/canteens/all')
}
