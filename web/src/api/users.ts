import { getAdminPage, put } from './http'
import type { AdminPage, UserAdminVO, UserListParams, UserStatusReq } from '@/types/common'

/** C2 用户管理：列表（排序 `createdAt DESC`） */
export function listUsers(params: UserListParams): Promise<AdminPage<UserAdminVO>> {
  return getAdminPage<UserAdminVO>('/admin/users', params)
}

/** C2 启用 / 禁用（只改 `status`） */
export function setUserStatus(id: number, req: UserStatusReq): Promise<null> {
  return put<null>(`/admin/users/${id}/status`, req)
}
