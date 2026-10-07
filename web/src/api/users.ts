import { del, getAdminPage, put } from './http'
import type { AdminPage, UserAdminVO, UserListParams, UserStatusReq } from '@/types/common'

/** C2 用户管理：列表（排序 `createdAt DESC`） */
export function listUsers(params: UserListParams): Promise<AdminPage<UserAdminVO>> {
  return getAdminPage<UserAdminVO>('/admin/users', params)
}

/** C2 启用 / 禁用（只改 `status`） */
export function setUserStatus(id: number, req: UserStatusReq): Promise<null> {
  return put<null>(`/admin/users/${id}/status`, req)
}

/** C2 解绑认证邮箱（`bind_email` → NULL，账号立即回落游客态；登录与已发表内容不受影响） */
export function unbindUserEmail(id: number): Promise<null> {
  return del<null>(`/admin/users/${id}/email`)
}

/** C2 删除账号（管理端代用户注销：匿名化、非物理删除，与本人自注销同口径） */
export function deleteUserAccount(id: number): Promise<null> {
  return del<null>(`/admin/users/${id}`)
}
