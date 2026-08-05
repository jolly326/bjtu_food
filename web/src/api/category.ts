import { del, get, post, put } from './http'

export interface CategoryItem {
  id: number
  name: string
  sortOrder: number
  status: string
}

/** 分类列表（全部，按 sortOrder 升序） */
export async function getAll(): Promise<CategoryItem[]> {
  return (await get<any>('/admin/categories')) as CategoryItem[]
}

/** 新增分类 */
export async function create(data: { name: string; sortOrder: number; status?: string }) {
  await post<void>('/admin/categories', data)
}

/** 编辑分类（名称 / 排序） */
export async function update(id: number, data: Partial<{ name: string; sortOrder: number }>) {
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
