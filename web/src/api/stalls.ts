import { get, post, put, del } from './http'
import type { StallVO, StallSaveReq } from '@/types/common'

/** B-04 档口：不分页（可选 canteenId 筛选）。 */
export function listStalls(canteenId?: number): Promise<StallVO[]> {
  return get<StallVO[]>('/admin/stalls', { canteenId })
}

export function createStall(req: StallSaveReq): Promise<StallVO> {
  return post<StallVO>('/admin/stalls', req)
}

export function updateStall(id: number, req: StallSaveReq): Promise<StallVO> {
  return put<StallVO>(`/admin/stalls/${id}`, req)
}

export function toggleStallStatus(id: number): Promise<StallVO> {
  return put<StallVO>(`/admin/stalls/${id}/status`)
}

export function deleteStall(id: number): Promise<null> {
  return del<null>(`/admin/stalls/${id}`)
}
