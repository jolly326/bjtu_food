import { del, get, post, put } from './http'
import type { OnOffStatus, ReportReasonAdminVO } from '@/types/common'

/**
 * A7 举报原因管理（`/admin/report-reasons`）。
 *
 * <p>契约真源：[A7-举报原因管理](../../../docs/api/web/report-reasons.md)。
 * <p><b>数据锚在原因 ID</b>（落库列 `user_feedback.sub_reason_id`，由后端生成）——
 * 没有「改 ID」的端点；改 `name` 免费。列表按原因 `id` 升序（字典不设排序位）。
 */

/** 原因列表（按 `id` 升序；**含已停用**；带 `feedbackCount`） */
export function listReportReasons(): Promise<ReportReasonAdminVO[]> {
  return get<ReportReasonAdminVO[]>('/admin/report-reasons')
}

/** 新增（默认**启用**、排在末尾；只填中文名，原因 ID 由后端生成；启用数上限 8） */
export function createReportReason(req: { name: string }): Promise<ReportReasonAdminVO> {
  return post<ReportReasonAdminVO>('/admin/report-reasons', req)
}

/** 改名（**只改 `name`**，改名免费） */
export function renameReportReason(id: number, req: { name: string }): Promise<null> {
  return put<null>(`/admin/report-reasons/${id}`, req)
}

/** 启停（停用最后一条启用 → 400；启用数超上限 → 400） */
export function updateReportReasonStatus(id: number, status: OnOffStatus): Promise<null> {
  return put<null>(`/admin/report-reasons/${id}/status`, { status })
}

/** 删除（**被举报记录引用 → 400**；下线一律用停用） */
export function deleteReportReason(id: number): Promise<null> {
  return del<null>(`/admin/report-reasons/${id}`)
}
