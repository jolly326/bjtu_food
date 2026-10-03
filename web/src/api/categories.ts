import { del, get, post, put } from './http'
import type { DishCategoryAdminVO, SortItemsReq } from '@/types/common'

/**
 * A6 菜品分类值字典（`/admin/dish-categories`，6 个端点）。
 *
 * <p>契约真源：[A6 的「值管理」节](../../../docs/web/A-主数据维护/A6-首页筛选视图管理.md) 与
 * [schema/dish_category_value.md](../../../docs/schema/dish_category_value.md)。
 * <p>分类值由**自由输入自动登记**产生（菜品录入 / 视图条件），本模块是它的**维护入口**：
 * 重命名（**改名免费**，数据锚在 `key`）/ 合并（清理同义值的唯一手段）/ 删除（**被菜品引用 → 400**）。
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

/** 排序（拖拽后整体提交全量行） */
export function sortCategories(req: SortItemsReq): Promise<null> {
  return put<null>('/admin/dish-categories/sort', req)
}

/** 合并（把 `fromId` 的菜品改指 `toId` 后删除 `fromId`） */
export function mergeCategory(fromId: number, toId: number): Promise<null> {
  return post<null>('/admin/dish-categories/merge', { fromId, toId })
}

/** 删除（**被菜品引用 → 400**；请改用合并） */
export function deleteCategory(id: number): Promise<null> {
  return del<null>(`/admin/dish-categories/${id}`)
}
