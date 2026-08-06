import type { User } from '@/types'
import { get, post, put } from './http'
import { pageRecords, userToLegacy } from './adapter'

export async function login(username: string, password: string): Promise<{ token: string; username: string }> {
  return await post('/auth/login', { account: username, password })
}

export async function getAll(): Promise<User[]> {
  return pageRecords(await get<any>('/admin/users', { page: 1, pageSize: 200 })).map(userToLegacy)
}

export async function getProfile(): Promise<User> {
  return userToLegacy(await get<any>('/auth/profile'))
}

export async function updateProfile(data: { nickname?: string; avatar?: string }): Promise<User> {
  return userToLegacy(await put<any>('/auth/profile', data))
}

export async function updatePassword(data: { oldPassword: string; newPassword: string }) {
  await put<void>('/auth/password', data)
}

export async function toggleUserStatusById(id: number) {
  const user = (await getAll()).find(item => Number(item.id) === id)
  await put<void>(`/admin/users/${id}/status`, { status: user?.status === 'active' ? 'disabled' : 'active' })
}
