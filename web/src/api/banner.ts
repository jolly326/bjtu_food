import type { Banner } from '@/types'
import { del, get, post, put } from './http'
import { imagesToLegacy, legacyToJsonImages } from './adapter'

function toBanner(raw: any, index = 0): Banner {
  return {
    id: (raw.id ?? index + 1) as unknown as bigint,
    title: raw.title || '',
    image: imagesToLegacy(raw.images ?? raw.image).split('|||')[0] || '',
    target_id: raw.targetId ?? raw.target_id,
    target_type: raw.targetType ?? raw.target_type,
    target_url: raw.targetUrl ?? raw.target_url ?? '',
    canteen_id: raw.canteenId ?? raw.canteen_id,
    sort_order: raw.sortOrder ?? raw.sort_order ?? index + 1,
    status: raw.status === 'disabled' ? 'inactive' : 'active',
    created_at: raw.createdAt || raw.created_at ? new Date(raw.createdAt || raw.created_at) : new Date(),
    updated_at: raw.updatedAt || raw.updated_at ? new Date(raw.updatedAt || raw.updated_at) : new Date(),
  }
}

function toApi(data: Partial<Banner>) {
  return {
    title: data.title,
    targetId: data.target_id,
    targetType: data.target_type,
    targetUrl: data.target_url,
    canteenId: data.canteen_id,
    sortOrder: data.sort_order,
    status: data.status === 'inactive' ? 'disabled' : 'enabled',
    images: legacyToJsonImages(data.image),
  }
}

export async function getAll(): Promise<Banner[]> {
  return (await get<any[]>('/admin/banners')).map(toBanner)
}

export async function create(data: Omit<Banner, 'id' | 'created_at' | 'updated_at'>) {
  await post<void>('/admin/banners', toApi(data))
}

export async function updateById(id: number, data: Partial<Banner>) {
  await put<void>(`/admin/banners/${id}`, toApi(data))
}

export async function deleteById(id: number) {
  await del<void>(`/admin/banners/${id}`)
}
