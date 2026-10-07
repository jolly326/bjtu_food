import { get, post, put } from './http'
import type { DishCategoryAdminVO } from '@/types/common'

/**
 * A6 菜品分类值字典（`/admin/dish-categories`）。
 *
 * <p>契约真源：[A6 的「值管理」节](../../../docs/api/web/categories.md)。
 * <p>本模块是分类值的维护入口：列表 / 登记 / 重命名（**改名免费**，数据锚在 `id`）；
 * `key` 是代码锚点，登记时**选填**、缺省由后端自动生成。
 */

/** 分类值列表（按 `order` 升序；带 `dishCount`） */
export function listCategories(): Promise<DishCategoryAdminVO[]> {
  return get<DishCategoryAdminVO[]>('/admin/dish-categories')
}

/** 登记新分类值（`key` 选填：小写字母/数字/`-`、全站唯一，缺省自动生成；`label` 应用层唯一） */
export function createCategory(label: string, key?: string): Promise<DishCategoryAdminVO> {
  return post<DishCategoryAdminVO>('/admin/dish-categories', key ? { key, label } : { label })
}

/** 重命名（**只改 `label`**） */
export function renameCategory(id: number, label: string): Promise<null> {
  return put<null>(`/admin/dish-categories/${id}`, { label })
}
