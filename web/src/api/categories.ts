import { get, post, put } from './http'
import type { DishCategoryAdminVO } from '@/types/common'

/**
 * A6 菜品分类值字典（`/admin/dish-categories`）。
 *
 * <p>契约真源：[A6 的「值管理」节](../../../docs/api/web/categories.md)。
 * <p>分类值由**自由输入自动登记**产生（菜品录入），本模块是它的维护入口：
 * 列表 / 登记 / 重命名（**改名免费**，数据锚在 `key`）。
 */

/** 分类值列表（按 `order` 升序；带 `dishCount`） */
export function listCategories(): Promise<DishCategoryAdminVO[]> {
  return get<DishCategoryAdminVO[]>('/admin/dish-categories')
}

/** 登记新分类值（`key` 小写字母/数字/`-`、全站唯一；`label` 应用层唯一） */
export function createCategory(key: string, label: string): Promise<DishCategoryAdminVO> {
  return post<DishCategoryAdminVO>('/admin/dish-categories', { key, label })
}

/** 重命名（**只改 `label`**） */
export function renameCategory(id: number, label: string): Promise<null> {
  return put<null>(`/admin/dish-categories/${id}`, { label })
}
