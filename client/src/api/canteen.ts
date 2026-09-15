import type { CanteenInfo, CanteenWithStalls } from '@/types/canteen'
import { get } from './http'
import type { RawRow } from './shared'

export async function getCanteenList(): Promise<CanteenInfo[]> {
  const rawList = await get<RawRow[]>('/canteens')
  return rawList.map((c: RawRow) => ({
    id: c.id != null ? Number(c.id) : undefined,
    name: c.name || '',
  }))
}

/**
 * 食堂含档口树（食堂 → stalls[]，GET /canteens/all）。
 * P2-11 / PR-12：返回定型为最小 DTO `CanteenWithStalls`（消费方只读 `name` / `stalls[].name`），
 * 消除此前 `RawRow[]`（=`Record<string, any>[]`）在反馈页位置选择器上的 `any` 逃逸；
 * 后端多余字段不映射、不透传。归一化边界说明见 shared.ts。
 */
export async function getCanteensWithStalls(): Promise<CanteenWithStalls[]> {
  const rows = await get<RawRow[]>('/canteens/all')
  return (rows || []).map((c: RawRow) => ({
    id: c.id != null ? Number(c.id) : undefined,
    name: typeof c.name === 'string' ? c.name : '',
    stalls: Array.isArray(c.stalls)
      ? (c.stalls as RawRow[])
          .map((s: RawRow) => ({
            id: s?.id != null ? Number(s.id) : undefined,
            name: typeof s?.name === 'string' ? s.name : '',
          }))
          .filter((s) => !!s.name)
      : [],
  }))
}
