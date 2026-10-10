import { del, get, post, put } from './http'
import type { DishViewAdminVO, DishViewCreateReq, DishViewUpdateReq } from '@/types/common'

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：[web/views.md](../../../docs/api/web/views.md)。
 * <p>视图是**纯数据**：文案 / 筛选种类 / 显隐全部落库，后台可自助新建 / 修改 / 删除，免发版。
 * 视图的筛选面只有「种类」一维（`categoryId`，`null` = 为你推荐），无可配条件语言；
 * 列表与端上下发一律按视图 `id` 升序（字典不设排序位）。
 */

/** 视图列表（按 `id` 升序；不分页；带 `matchedCount`） */
export function listViews(): Promise<DishViewAdminVO[]> {
  return get<DishViewAdminVO[]>('/admin/dish-views')
}

/** 新建视图（默认启用、`id` 自增 ⇒ 排在末尾；`categoryId` 缺省 = 为你推荐） */
export function createView(req: DishViewCreateReq): Promise<DishViewAdminVO> {
  return post<DishViewAdminVO>('/admin/dish-views', req)
}

/** 修改视图（文案 / 筛选种类 / 启停整体替换；停用「最后一个启用的视图」→ 400） */
export function updateView(id: number, req: DishViewUpdateReq): Promise<null> {
  return put<null>(`/admin/dish-views/${id}`, req)
}

/** 删除视图（删除「最后一个启用的视图」→ 400） */
export function deleteView(id: number): Promise<null> {
  return del<null>(`/admin/dish-views/${id}`)
}
