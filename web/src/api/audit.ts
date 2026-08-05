import type { AuditVO, Review } from '@/types'
import { del, get, post, put } from './http'
import { pageRecords, auditToLegacy, reviewToLegacy } from './adapter'

/** 审核类型：菜品 / 档口 / 食堂（社区动态审核走 /admin/moments，不在此列） */
export type AuditType = 'dish' | 'stall' | 'canteen'

/** 待审核 / 指定状态列表（分页） */
export async function listAudit(type: AuditType, status: string, page = 1, pageSize = 20): Promise<AuditVO[]> {
  return pageRecords(
    await get<any>('/admin/audit', { type, status, page, pageSize }),
  ).map(auditToLegacy)
}

/** 通过：置 audit_status=approved */
export async function approveAudit(type: AuditType, id: number) {
  await post<void>(`/admin/audit/${type}/${id}/approve`)
}

/** 退回：置 audit_status=rejected + 写 reject_reason（必填） */
export async function rejectAudit(type: AuditType, id: number, rejectReason: string) {
  await post<void>(`/admin/audit/${type}/${id}/reject`, { rejectReason })
}

/** 评价列表（分页，可按 isHidden 过滤） */
export async function listReviews(isHidden?: boolean, page = 1, pageSize = 200): Promise<Review[]> {
  const params: Record<string, unknown> = { page, pageSize }
  if (isHidden !== undefined) params.isHidden = isHidden
  return pageRecords(await get<any>('/admin/reviews', params)).map(reviewToLegacy)
}

/** 设置评价隐藏 / 显示（is_hidden 控制可见性，显式语义） */
export async function setReviewHidden(id: number, hidden: boolean) {
  await put<void>(`/admin/reviews/${id}/hide`, { hidden })
}

/** 删除评价（不当图片可单独删除，破坏性操作） */
export async function deleteReview(id: number) {
  await del<void>(`/admin/reviews/${id}`)
}
