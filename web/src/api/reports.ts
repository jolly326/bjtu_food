import { getAdminPage, put } from './http'
import type {
  AdminPage,
  ModerationHandleReq,
  ReportAdminVO,
  ReportListParams,
} from '@/types/common'

/**
 * 举报管理（`/admin/reports`）。
 *
 * <p>契约真源：[web/reports.md](../../../docs/api/web/reports.md)。
 * 举报**无正文**：结论以结构化原因（`optionId`）为准；意见反馈是独立资源，
 * 走 `api/feedbacks.ts`（`/admin/feedbacks`）。
 */
export function listReports(params: ReportListParams): Promise<AdminPage<ReportAdminVO>> {
  return getAdminPage<ReportAdminVO>('/admin/reports', params)
}

/**
 * 处置（受理 / 不予处理；受理可联动隐藏被举报评价）。
 *
 * <p>`hideReview` 仅受理时消费；`handled + hideReview=true` 幂等
 * （评价已隐藏 / 已删除 / 举报目标已删除时静默成功）。
 */
export function handleReport(id: number, req: ModerationHandleReq): Promise<null> {
  return put<null>(`/admin/reports/${id}`, req)
}
