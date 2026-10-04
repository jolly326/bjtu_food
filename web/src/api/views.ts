import { get, put } from './http'
import { submitSort } from './shared'
import type { SortItemsReq, DishViewAdminVO, DishViewUpdateReq } from '@/types/common'

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：[A6-首页筛选视图管理](../../../docs/api/web/views.md)。
 * <p>后台只维护 tab 的**文案 / 顺序 / 显隐**；视图的筛选条件与排序口径属 seed / 代码资产，
 * 新增 / 删除 tab 亦属代码改动 —— 故本模块只有列表 / 修改 / 排序三个端点。
 */

/** 视图列表（按 `order` 升序；不分页） */
export function listViews(): Promise<DishViewAdminVO[]> {
  return get<DishViewAdminVO[]>('/admin/dish-views')
}

/** 修改视图（仅 `label` / `enabled`；停用「最后一个启用的视图」→ 400） */
export function updateView(id: number, req: DishViewUpdateReq): Promise<null> {
  return put<null>(`/admin/dish-views/${id}`, req)
}

/** 排序（拖拽后整体提交全量行） */
export function sortViews(req: SortItemsReq): Promise<null> {
  return submitSort('/admin/dish-views/sort', req)
}
