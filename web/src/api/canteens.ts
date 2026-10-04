import { get, post, put, del } from './http'
import type { CanteenAdminVO, CanteenSaveReq } from '@/types/common'

/** A1 食堂列表（按 `name` 升序；**不分页**，量级为十数条） */
export function listCanteens(): Promise<CanteenAdminVO[]> {
  return get<CanteenAdminVO[]>('/admin/canteens')
}

/** A1 新增（名称应用层查重，重名 / 空 / 超长 → 400） */
export function createCanteen(req: CanteenSaveReq): Promise<CanteenAdminVO> {
  return post<CanteenAdminVO>('/admin/canteens', req)
}

/** A1 改名（可编辑字段仅 `name`，整体替换） */
export function updateCanteen(id: number, req: CanteenSaveReq): Promise<null> {
  return put<null>(`/admin/canteens/${id}`, req)
}

/** A1 删除（**其下仍有档口 → 400**，避免孤儿档口） */
export function deleteCanteen(id: number): Promise<null> {
  return del<null>(`/admin/canteens/${id}`)
}
