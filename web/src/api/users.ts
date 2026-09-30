import { getPage, put } from './http'
import type { UserAdminVO, UserListParams, UserStatusReq } from '@/types/common'

/** B-12 用户管理：分页 + 筛选。 */
export function listUsers(params: UserListParams): Promise<UserAdminVO[]> {
  return getPage<UserAdminVO>('/admin/users', params)
}

export function setUserStatus(id: number, req: UserStatusReq): Promise<UserAdminVO> {
  return put<UserAdminVO>(`/admin/users/${id}/status`, req)
}
