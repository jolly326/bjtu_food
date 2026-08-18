import type { CanteenInfo } from '@/types/canteen'
import { get } from './http'
import { normalizeImages } from './_shared'

function firstImage(raw: any): string {
  return normalizeImages(raw?.images ?? raw?.image ?? raw?.icon)[0] || ''
}

export async function getCanteenList(): Promise<CanteenInfo[]> {
  const rawList = await get<any[]>('/canteens')
  return rawList.map((c: any) => ({
    name: c.name || '',
    location: c.location || c.description || '',
    icon: firstImage(c),
    latitude: c.latitude != null ? Number(c.latitude) : undefined,
    longitude: c.longitude != null ? Number(c.longitude) : undefined,
    distance: c.distance != null ? Number(c.distance) : undefined,
  }))
}

export async function getCanteenImages(): Promise<Record<string, string>> {
  const rawMap = await get<Record<string, unknown>>('/canteens/images')
  return Object.fromEntries(
    Object.entries(rawMap || {}).map(([name, value]) => [name, normalizeImages(value)[0] || '']),
  )
}

export async function getCanteensWithStalls(): Promise<any[]> {
  return await get<any[]>('/canteens/all')
}
