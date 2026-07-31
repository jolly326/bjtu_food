/**
 * 档口接口模块（project_spec.md §3.x.5：GET /stalls?canteenId=）
 */
import { get } from './http'
import type { StallInfo, MyPublishStall } from '@/types/canteen'
import { toAbsoluteImageUrl } from '@/utils/image'

export async function getStallsByCanteen(canteenId: number): Promise<StallInfo[]> {
  return get<StallInfo[]>('/stalls', { canteenId })
}

function parseImages(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((v): v is string => typeof v === 'string' && !!v).map(toAbsoluteImageUrl)
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parseImages(parsed) : [toAbsoluteImageUrl(value)]
  } catch {
    return value.split('|||').map(v => v.trim()).filter(Boolean).map(toAbsoluteImageUrl)
  }
}

/**
 * 我的档口·食堂提交列表（GET /my/stalls，STUDENT）
 * 后端按 created_by 返回当前学生提交的档口/食堂（含 auditStatus / rejectReason）。
 * 后端以 Stall 表承载两种 type，需按 canteenId 是否存在推断 stall / canteen。
 */
export async function getMyStalls(): Promise<MyPublishStall[]> {
  const res = await get<any>('/my/stalls')
  const list: any[] = Array.isArray(res) ? res : (res?.records || res?.list || [])
  return list.map((raw) => ({
    id: Number(raw.id),
    type: raw.canteenId == null ? 'canteen' : 'stall',
    name: raw.name || '',
    location: raw.location || '',
    description: raw.description || '',
    images: parseImages(raw.images),
    auditStatus: raw.auditStatus || 'pending',
    rejectReason: raw.rejectReason || '',
    createTime: raw.createdAt || raw.createTime || '',
  }))
}
