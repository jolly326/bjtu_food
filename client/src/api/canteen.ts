import type { CanteenInfo } from '@/types/canteen'
import { get } from './http'
import { firstImage, type RawRow } from './shared'

export async function getCanteenList(): Promise<CanteenInfo[]> {
  const rawList = await get<RawRow[]>('/canteens')
  return rawList.map((c: RawRow) => ({
    id: c.id != null ? Number(c.id) : undefined,
    name: c.name || '',
    location: c.location || c.description || '',
    icon: firstImage(c),
    latitude: c.latitude != null ? Number(c.latitude) : undefined,
    longitude: c.longitude != null ? Number(c.longitude) : undefined,
    distance: c.distance != null ? Number(c.distance) : undefined,
  }))
}

/** 食堂含档口树（食堂 → stalls[]；RawRow 载体见 shared.ts 归一化边界说明） */
export async function getCanteensWithStalls(): Promise<RawRow[]> {
  return await get<RawRow[]>('/canteens/all')
}
