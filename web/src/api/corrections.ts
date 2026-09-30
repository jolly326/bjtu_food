import { getPage, post, put } from './http'
import type {
  CorrectionAdminVO,
  CorrectionListParams,
  CorrectionAdoptReq,
  StallConfirmVO,
  CorrectionRejectReq,
} from '@/types/common'

/** B-11 菜品纠错管理：分页 + 筛选。 */
export function listCorrections(
  params: CorrectionListParams,
): Promise<CorrectionAdminVO[]> {
  return getPage<CorrectionAdminVO>('/admin/corrections', params)
}

/**
 * 采纳纠错。响应二义性：data=null 表示采纳已完成；
 * data=StallConfirmVO（needStallConfirm=true）表示需管理员确认档口后再次调用。
 */
export function adoptCorrection(
  id: number,
  req?: CorrectionAdoptReq,
): Promise<StallConfirmVO | null> {
  return post<StallConfirmVO | null>(`/admin/corrections/${id}/adopt`, req ?? {})
}

export function rejectCorrection(
  id: number,
  req: CorrectionRejectReq,
): Promise<CorrectionAdminVO> {
  return put<CorrectionAdminVO>(`/admin/corrections/${id}`, req)
}
