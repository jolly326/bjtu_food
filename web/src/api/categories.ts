import { get, post, put } from './http'
import type { DishCategoryAdminVO } from '@/types/common'

/**
 * A6 菜品种类字典（`/admin/dish-categories`，落库表 `dish_category`）。
 *
 * <p>契约真源：[web/categories.md](../../../docs/api/web/categories.md)。
 * <p>本模块封装**种类字典**的列表 / 登记 / 重命名：
 * 种类是独立字典表，数据锚在其 `id` 上 ⇒ **改名免费**（菜品侧零迁移）；
 * 被菜品引用时不可删除，本端点也不提供删除。
 * 管理员只填中文名，ID 由后端生成；列表按种类 `id` 升序（字典不设排序位）。
 * <p>维护入口挂在 A3 菜品管理（分类下拉旁），**不单设菜单项**（见
 * [列表页模板.md](../../../docs/ui/web/列表页模板.md)）。
 */

/** 种类列表（按 `id` 升序；带 `dishCount`） */
export function listCategories(): Promise<DishCategoryAdminVO[]> {
  return get<DishCategoryAdminVO[]>('/admin/dish-categories')
}

/** 登记新种类（只填中文名；ID 由后端生成，新建项排在末尾） */
export function createCategory(name: string): Promise<DishCategoryAdminVO> {
  return post<DishCategoryAdminVO>('/admin/dish-categories', { name })
}

/** 重命名（**只改 `name`**；同表重名 → 400） */
export function renameCategory(id: number, name: string): Promise<null> {
  return put<null>(`/admin/dish-categories/${id}`, { name })
}
