import { get, post, put, del } from './http'
import type { CanteenVO, CanteenSaveReq } from '@/types/common'

/** B-03 食堂：不分页，直接返回数组（量级为十数条）。 */
export function listCanteens(): Promise<CanteenVO[]> {
  return get<CanteenVO[]>('/admin/canteens')
}

export function createCanteen(req: CanteenSaveReq): Promise<CanteenVO> {
  return post<CanteenVO>('/admin/canteens', req)
}

export function updateCanteen(id: number, req: CanteenSaveReq): Promise<CanteenVO> {
  return put<CanteenVO>(`/admin/canteens/${id}`, req)
}

export function toggleCanteenStatus(id: number): Promise<CanteenVO> {
  return put<CanteenVO>(`/admin/canteens/${id}/status`)
}

export function deleteCanteen(id: number): Promise<null> {
  return del<null>(`/admin/canteens/${id}`)
}
