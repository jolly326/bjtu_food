import { del, get, post, put } from './http'
import { submitSort } from './shared'
import type {
  SortItemsReq,
  DishViewAdminVO,
  DishViewCreateReq,
  DishViewUpdateReq,
} from '@/types/common'

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：[web/views.md](../../../docs/api/web/views.md)。
 * <p>视图是**纯数据**：文案 / 顺序 / 显隐 / **筛选条件** / **排序口径**全部落库，
 * 后台可自助新建 / 修改 / 删除，免发版；条件与排序口径受后端白名单约束（不可配 SQL）。
 */

/** 视图列表（按 `order` 升序；不分页；带 `conditions` / `sortKind` / `matchedCount`） */
export function listViews(): Promise<DishViewAdminVO[]> {
  return get<DishViewAdminVO[]>('/admin/dish-views')
}

/** 新建视图（默认启用、排最后；`conditions` 为 `[]` 表示不筛选） */
export function createView(req: DishViewCreateReq): Promise<DishViewAdminVO> {
  return post<DishViewAdminVO>('/admin/dish-views', req)
}

/** 修改视图（文案 / 启停 / 条件 / 排序口径整体替换；停用「最后一个启用的视图」→ 400） */
export function updateView(id: number, req: DishViewUpdateReq): Promise<null> {
  return put<null>(`/admin/dish-views/${id}`, req)
}

/** 删除视图（删除「最后一个启用的视图」→ 400） */
export function deleteView(id: number): Promise<null> {
  return del<null>(`/admin/dish-views/${id}`)
}

/** 排序（拖拽后整体提交全量行） */
export function sortViews(req: SortItemsReq): Promise<null> {
  return submitSort('/admin/dish-views/sort', req)
}
