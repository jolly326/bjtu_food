import { del, get, post, put } from './http'
import { submitSort } from './shared'
import type { OnOffStatus, ReportReasonAdminVO, SortItemsReq } from '@/types/common'

/**
 * A7 举报原因管理（`/admin/report-reasons`，6 个端点）。
 *
 * <p>契约真源：[A7-举报原因管理](../../../docs/web/A-主数据维护/A7-举报原因管理.md)。
 * <p><b>没有「改机器值」的端点</b> —— `value` 是历史举报的数据锚点（落库列 `user_feedback.sub`），
 * 在用后不可改；要改就停用旧值、新建一个。
 */

/** 原因列表（按 `order` 升序；**含已停用**；带 `feedbackCount`） */
export function listReportReasons(): Promise<ReportReasonAdminVO[]> {
  return get<ReportReasonAdminVO[]>('/admin/report-reasons')
}

/** 新增（默认**启用**、排最后；机器值 1~32 小写字母/数字/`-`、全站唯一；启用数上限 8） */
export function createReportReason(req: {
  value: string
  label: string
}): Promise<ReportReasonAdminVO> {
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
