import type { Banner } from '@/types'
import { findIdx, nextId } from './common'
import { get } from './http'
import { imagesToLegacy } from './adapter'

let localDirty = false

const mockData: Banner[] = [
  { id: 1 as unknown as bigint, title: '开学季优惠', image: '', type: 'carousel', sort_order: 1, status: 'active', created_at: new Date('2024-09-01'), updated_at: new Date('2024-09-01') },
]

function toBanner(raw: any, index: number): Banner {
  return {
    id: (raw.id ?? index + 1) as unknown as bigint,
    title: raw.title || '',
    image: imagesToLegacy(raw.images ?? raw.image).split('|||')[0] || '',
    type: raw.type || 'carousel',
    target_id: raw.targetId ?? raw.target_id,
    target_type: raw.targetType ?? raw.target_type,
    canteen_id: raw.canteenId ?? raw.canteen_id,
    sort_order: raw.sortOrder ?? raw.sort_order ?? index + 1,
    status: raw.status === 'disabled' ? 'inactive' : 'active',
    created_at: raw.createdAt || raw.created_at || new Date(),
    updated_at: raw.updatedAt || raw.updated_at || new Date(),
  }
}

export async function getAll(): Promise<Banner[]> {
  if (localDirty) return [...mockData]

  try {
    const banners = (await get<any[]>('/canteens/banners')).map(toBanner)
    mockData.splice(0, mockData.length, ...banners)
    return [...mockData]
  } catch {
    console.log('[banner] 降级到 Mock')
    return [...mockData]
  }
}

export async function create(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) {
  console.warn('[banner] 后端暂未实现 Banner 后台新增接口，当前仅本地维护')
  localDirty = true
  const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as Banner
  mockData.push(item)
  return item
}

export async function updateById(id: number, data: Partial<Banner>) {
  console.warn('[banner] 后端暂未实现 Banner 后台编辑接口，当前仅本地维护')
  localDirty = true
  const idx = findIdx(mockData, id)
  if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
}

export async function deleteById(id: number) {
  console.warn('[banner] 后端暂未实现 Banner 后台删除接口，当前仅本地维护')
  localDirty = true
  const idx = findIdx(mockData, id)
  if (idx !== -1) mockData.splice(idx, 1)
}
