import type { BannerItem, CanteenInfo, StallDetail } from '@/types/canteen'
import { API_BASE_URL } from './config'
import { get } from './http'

function normalizeImages(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string' && item.length > 0).map(toAbsoluteImageUrl)
  if (typeof value !== 'string' || !value.trim()) return []
  const text = value.trim()
  try {
    const parsed = JSON.parse(text)
    return Array.isArray(parsed) ? normalizeImages(parsed) : [toAbsoluteImageUrl(text)]
  } catch {
    return text.split('|||').map(item => item.trim()).filter(Boolean).map(toAbsoluteImageUrl)
  }
}

function toAbsoluteImageUrl(url: string): string {
  if (!url || /^(https?:|data:|blob:)/i.test(url) || url.startsWith('/static/')) return url
  if (url.startsWith('/images/') || url.startsWith('/uploads/')) return `${API_BASE_URL}${url}`
  return url
}

function firstImage(raw: any): string {
  return normalizeImages(raw?.images ?? raw?.image ?? raw?.icon)[0] || ''
}

export async function getHomeBanners(): Promise<BannerItem[]> {
  const raw = await get<any[]>('/canteens/banners')
  return raw.map((b: any) => ({
    title: b.title || '',
    subtitle: b.subtitle || '',
    image: firstImage(b),
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
    name: raw.name || stallName,
    images: normalizeImages(raw.images ?? raw.image),
    location: raw.location || canteen,
    description: raw.description || '',
  }
}

export async function getCanteensWithStalls(): Promise<any[]> {
  return await get<any[]>('/canteens/all')
}
