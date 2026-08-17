import { get } from './http'

/** 品类（GET /categories：首页品类滚轮数据源） */
export interface CategoryItem {
  id: number
  /** 机器标识（唯一，如 noodle/rice/home/malatang/bbq/porridge/drink），滚轮 key 用 */
  code: string
  name: string
  sortOrder: number
}

/** 首页品类滚轮数据：GET /categories（公开接口，enabled 品类按 sortOrder 升序） */
export async function getCategories(): Promise<CategoryItem[]> {
  const raw = await get<any[]>('/categories')
  return (raw || []).map((c: any) => ({
    id: Number(c.id),
    code: String(c.code || ''),
    name: c.name || '',
    sortOrder: Number(c.sortOrder ?? 0),
  }))
}
