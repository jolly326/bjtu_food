import { del, get, post, put } from './http'

export interface CategoryItem {
  id: number
  /** 品类机器标识（唯一，前端滚轮 key 与筛选用，如 noodle/rice/home/malatang/bbq/porridge/drink） */
  code: string
  name: string
  sortOrder: number
  status: string
}

/** 品类列表（全部，按 sortOrder 升序） */
export async function getAll(): Promise<CategoryItem[]> {
  return (await get<any>('/admin/categories')) as CategoryItem[]
}

/** 新增品类 */
export async function create(data: { code: string; name: string; sortOrder: number; status?: string }) {
  await post<void>('/admin/categories', data)
}

/** 编辑品类（名称 / code / 排序） */
export async function update(id: number, data: Partial<{ code: string; name: string; sortOrder: number }>) {
  await put<void>(`/admin/categories/${id}`, data)
}

/** 启停分类 */
export async function toggleStatus(id: number, status: 'enabled' | 'disabled') {
  await put<void>(`/admin/categories/${id}/status`, { status })
}

/** 删除分类 */
export async function remove(id: number) {
  await del<void>(`/admin/categories/${id}`)
}
