import { get, post, put } from './http'
import { submitSort } from './shared'
import type { DishCategoryAdminVO, SortItemsReq } from '@/types/common'

/**
 * A6 菜品种类字典（`/admin/dish-categories`）。
 *
 * <p>契约真源：[web/categories.md](../../../docs/api/web/categories.md)。
 * <p>本模块是**系统维度（菜品种类）取值的别名面**：列表 / 登记 / 重命名 / **拖拽排序**
 * （**改名免费**，数据锚在取值 `id`）；管理员只填中文名，ID 由后端生成。
 * 同一批行也可经 A4 的取值端点读写（见 `api/dimensions.ts`）。
 * <p>维护入口挂在 A3 菜品管理（分类下拉旁），**不单设菜单项**（见
 * [列表页模板.md](../../../docs/ui/web/列表页模板.md)）。
 */

/** 种类取值列表（按 `order` 升序；带 `dishCount`） */
export function listCategories(): Promise<DishCategoryAdminVO[]> {
  return get<DishCategoryAdminVO[]>('/admin/dish-categories')
}

/** 登记新种类取值（只填中文名；ID 由后端生成、组内顺序由拖拽维护） */
export function createCategory(label: string): Promise<DishCategoryAdminVO> {
  return post<DishCategoryAdminVO>('/admin/dish-categories', { label })
}

/** 重命名（**只改 `label`**） */
export function renameCategory(id: number, label: string): Promise<null> {
  return put<null>(`/admin/dish-categories/${id}`, { label })
}

/** 排序（拖拽后**整体提交全量行**；缺行 / 重复 → `400`） */
export function sortCategories(req: SortItemsReq): Promise<null> {
  return submitSort('/admin/dish-categories/sort', req)
}
