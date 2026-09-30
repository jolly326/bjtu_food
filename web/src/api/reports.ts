import { getPage, put } from './http'
import type { ReportAdminVO, ReportListParams, ReportHandleReq } from '@/types/common'

/** B-10 举报管理：仅 type=report，分页 + 筛选。 */
export function listReports(params: ReportListParams): Promise<ReportAdminVO[]> {
  return getPage<ReportAdminVO>('/admin/reports', params)
}

export function handleReport(
  id: number,
  req: ReportHandleReq,
): Promise<ReportAdminVO> {
  return put<ReportAdminVO>(`/admin/reports/${id}`, req)
}
