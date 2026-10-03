import { del, get, post, put } from './http'
import type {
  SortItemsReq,
  DishViewAdminVO,
  DishViewPreviewVO,
  DishViewSaveReq,
} from '@/types/common'

/**
 * A6 首页筛选视图管理（`/admin/dish-views`，7 个端点）。
 *
 * <p>契约真源：[A6-首页筛选视图管理](../../../docs/web/A-主数据维护/A6-首页筛选视图管理.md)。
 * <p>视图 = **筛选条件（字段白名单）+ 排序**；条件由服务端翻译为参数化 WHERE，
 * 后台**不可**写 SQL —— 非法字段 / 操作符 / 取值一律 `400`。
 * <p>下发规则：`enabled` + **匹配数为 0 不下发** + 默认视图恒下发（默认视图**不可删除、不可停用**）。
 */

/** 视图列表（按 `order` 升序；不分页） */
export function listViews(): Promise<DishViewAdminVO[]> {
  return get<DishViewAdminVO[]>('/admin/dish-views')
}

/** 新建视图（**恒为非默认**；`order` 默认排最后） */
export function createView(req: DishViewSaveReq): Promise<DishViewAdminVO> {
  return post<DishViewAdminVO>('/admin/dish-views', req)
}

/** 修改视图（`key` 不可改；改键 / 停用默认视图 → 400） */
export function updateView(id: number, req: DishViewSaveReq): Promise<null> {
  return put<null>(`/admin/dish-views/${id}`, req)
}

/** 删除视图（默认视图 → 400） */
export function deleteView(id: number): Promise<null> {
  return del<null>(`/admin/dish-views/${id}`)
}

/** 排序（拖拽后整体提交全量行） */
export function sortViews(req: SortItemsReq): Promise<null> {
  return put<null>('/admin/dish-views/sort', req)
}

/** 设为默认（自动取消原默认） */
export function setDefaultView(id: number): Promise<null> {
  return put<null>(`/admin/dish-views/${id}/default`)
}

/**
 * 预览匹配数（**保存前试算，不入库**）。
 *
 * <p>服务端只读 `conditions` —— 故这里复用保存体的形状，`label` / `key` 传占位值即可
 * （缺它们会被 Bean Validation 拦下，而预览本不需要）。
 */
export function previewView(req: DishViewSaveReq): Promise<DishViewPreviewVO> {
  return post<DishViewPreviewVO>('/admin/dish-views/preview', req)
}
