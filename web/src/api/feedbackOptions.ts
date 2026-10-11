import { del, get, post, put } from './http'
import type { OnOffStatus, OptionAdminVO, OptionSaveReq } from '@/types/common'

/**
 * 反馈类型字典管理（`/admin/feedback-options`）。
 *
 * <p>契约真源：[web/feedback-options.md](../../../docs/api/web/feedback-options.md)。
 * <p>维护 client 意见反馈页的「反馈类型」单选字典 —— 新增类型**免发版、免客户端改动**。
 * <p>与举报原因字典**同族**：不变量与出参形状完全一致（`OptionAdminVO`），
 * 差异只在「被哪张工单表引用」（落库列 `feedback.option_id`）。
 * <p><b>数据锚在类型 ID</b>（由后端生成）—— 没有「改 ID」的端点；改 `name` 免费
 * （历史反馈的中文翻译实时生效）。列表按类型 `id` 升序（字典不设排序位）。
 */

/** 类型列表（按 `id` 升序；**含已停用**；带 `usageCount`） */
export function listFeedbackOptions(): Promise<OptionAdminVO[]> {
  return get<OptionAdminVO[]>('/admin/feedback-options')
}

/** 新增（默认**启用**、排在末尾；只填中文名，类型 ID 由后端生成；启用数上限 8） */
export function createFeedbackOption(req: OptionSaveReq): Promise<OptionAdminVO> {
  return post<OptionAdminVO>('/admin/feedback-options', req)
}

/** 改名（**只改 `name`**，改名免费） */
export function renameFeedbackOption(id: number, req: OptionSaveReq): Promise<null> {
  return put<null>(`/admin/feedback-options/${id}`, req)
}

/** 启停（停用最后一条启用 → 400；启用数超上限 → 400） */
export function updateFeedbackOptionStatus(id: number, status: OnOffStatus): Promise<null> {
  return put<null>(`/admin/feedback-options/${id}/status`, { status })
}

/** 删除（**被反馈记录引用 → 400**；下线一律用停用） */
export function deleteFeedbackOption(id: number): Promise<null> {
  return del<null>(`/admin/feedback-options/${id}`)
}
