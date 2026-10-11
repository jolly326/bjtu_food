import { del, get, post, put } from './http'
import type { OnOffStatus, OptionAdminVO, OptionSaveReq } from '@/types/common'

/**
 * 举报原因字典管理（`/admin/report-options`）。
 *
 * <p>契约真源：[web/report-options.md](../../../docs/api/web/report-options.md)。
 * <p>维护 client 举报弹层的「原因」单选字典 —— 新增原因**免发版、免客户端改动**。
 * <p><b>数据锚在原因 ID</b>（落库列 `report.option_id`，由后端生成）—— 没有「改 ID」的端点；
 * 改 `name` 免费（历史举报的中文翻译实时生效）。列表按原因 `id` 升序（字典不设排序位）。
 */

/** 原因列表（按 `id` 升序；**含已停用**；带 `usageCount`） */
export function listReportOptions(): Promise<OptionAdminVO[]> {
  return get<OptionAdminVO[]>('/admin/report-options')
}

/** 新增（默认**启用**、排在末尾；只填中文名，原因 ID 由后端生成；启用数上限 8） */
export function createReportOption(req: OptionSaveReq): Promise<OptionAdminVO> {
  return post<OptionAdminVO>('/admin/report-options', req)
}

/** 改名（**只改 `name`**，改名免费） */
export function renameReportOption(id: number, req: OptionSaveReq): Promise<null> {
  return put<null>(`/admin/report-options/${id}`, req)
}

/** 启停（停用最后一条启用 → 400；启用数超上限 → 400） */
export function updateReportOptionStatus(id: number, status: OnOffStatus): Promise<null> {
  return put<null>(`/admin/report-options/${id}/status`, { status })
}

/** 删除（**被举报记录引用 → 400**；下线一律用停用） */
export function deleteReportOption(id: number): Promise<null> {
  return del<null>(`/admin/report-options/${id}`)
}
