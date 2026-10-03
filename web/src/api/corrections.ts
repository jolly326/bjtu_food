import { getAdminPage, get, post, put } from './http'
import type {
  AdminPage,
  CorrectionAdminVO,
  CorrectionDetailVO,
  CorrectionListParams,
  CorrectionAdoptReq,
  CorrectionRejectReq,
  StallConfirmVO,
} from '@/types/common'

/** B4 纠错列表（排序 `createdAt DESC`） */
export function listCorrections(
  params: CorrectionListParams,
): Promise<AdminPage<CorrectionAdminVO>> {
  return getAdminPage<CorrectionAdminVO>('/admin/corrections', params)
}

/** B4 单条详情（含 `differences` 差异对照，供采纳抽屉逐项勾选） */
export function getCorrection(id: number): Promise<CorrectionDetailVO> {
  return get<CorrectionDetailVO>(`/admin/corrections/${id}`)
}

/**
 * B4 采纳（**逐项**；两段式档口确认）。
 * 响应二义性：`data = null` 表示采纳已完成；`data = StallConfirmVO`（`needStallConfirm`）表示需先确认档口后再次调用。
 */
export function adoptCorrection(
  id: number,
  req?: CorrectionAdoptReq,
): Promise<StallConfirmVO | null> {
  return post<StallConfirmVO | null>(`/admin/corrections/${id}/adopt`, req ?? {})
}

/** B4 拒绝（不采纳） */
export function rejectCorrection(id: number, req: CorrectionRejectReq): Promise<null> {
  return put<null>(`/admin/corrections/${id}`, req)
}
