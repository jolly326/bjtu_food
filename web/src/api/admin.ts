import type { AdminUser } from '@/types'
import { del, get, post, put } from './http'
import { pageRecords, adminUserToLegacy } from './adapter'

/** 后台管理员账号列表 */
export async function getAll(page = 1, pageSize = 200): Promise<AdminUser[]> {
  return pageRecords(await get<any>('/admin/admins', { page, pageSize })).map(adminUserToLegacy)
}

/** 新增管理员账号（含初始密码） */
export async function create(data: { username: string; password: string; nickname?: string }) {
  await post<void>('/admin/admins', data)
}

/** 编辑管理员（昵称 / 密码，密码为空则不改） */
export async function updateById(id: number, data: { nickname?: string; password?: string }) {
  await put<void>(`/admin/admins/${id}`, data)
}

/** 禁用 / 启用管理员 */
export async function setStatus(id: number, status: 'active' | 'disabled') {
  await put<void>(`/admin/admins/${id}/status`, { status })
}

/** 删除管理员 */
export async function deleteById(id: number) {
  await del<void>(`/admin/admins/${id}`)
}

/** 当前登录管理员是否为超级管理员（用于「管理员管理」入口可见性判断） */
export async function getMyRole(): Promise<string> {
  const me = adminUserToLegacy(await get<any>('/admin/admins/me'))
  return me.role
}
