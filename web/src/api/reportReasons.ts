import { del, get, post, put } from './http'
import { submitSort } from './shared'
import type { OnOffStatus, ReportReasonAdminVO, SortItemsReq } from '@/types/common'

/**
 * A7 举报原因管理（`/admin/report-reasons`，6 个端点）。
 *
 * <p>契约真源：[A7-举报原因管理](../../../docs/api/web/report-reasons.md)。
 * <p><b>数据锚在原因 ID</b>（落库列 `user_feedback.sub_reason_id`，由后端生成）——
 * 没有「改 ID」的端点；改 `label` 免费。
 */

/** 原因列表（按 `order` 升序；**含已停用**；带 `feedbackCount`） */
export function listReportReasons(): Promise<ReportReasonAdminVO[]> {
  return get<ReportReasonAdminVO[]>('/admin/report-reasons')
}

/** 新增（默认**启用**、排最后；只填中文标签，原因 ID 由后端生成；启用数上限 8） */
export function createReportReason(req: { label: string }): Promise<ReportReasonAdminVO> {
  return post<ReportReasonAdminVO>('/admin/report-reasons', req)
}

/** 改名（**只改 `label`**，改名免费） */
export function renameReportReason(id: number, req: { label: string }): Promise<null> {
  return put<null>(`/admin/report-reasons/${id}`, req)
}

/** 启停（停用最后一条启用 → 400；启用数超上限 → 400） */
export function updateReportReasonStatus(id: number, status: OnOffStatus): Promise<null> {
  return put<null>(`/admin/report-reasons/${id}/status`, { status })
}

/** 排序（拖拽后整体提交全量行） */
export function sortReportReasons(req: SortItemsReq): Promise<null> {
  return submitSort('/admin/report-reasons/sort', req)
}

/** 删除（**被举报记录引用 → 400**；下线一律用停用） */
export function deleteReportReason(id: number): Promise<null> {
  return del<null>(`/admin/report-reasons/${id}`)
}
