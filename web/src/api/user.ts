import type { User } from '@/types'
import { findIdx, nextId } from './common'
import { get, post, put } from './http'
import { pageRecords, userToLegacy } from './adapter'

const mockData: User[] = [
  { id: 100 as unknown as bigint, username: 'admin001', password: '123456', nickname: '管理员', avatar: '', role: 'admin', status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
]

export async function login(username: string, password: string): Promise<{ token: string; username: string }> {
  return await post('/auth/login', { account: username, password })
}

export async function getAll(): Promise<User[]> {
  try {
    return pageRecords(await get<any>('/admin/users', { page: 1, pageSize: 200 })).map(userToLegacy)
  } catch {
    console.log('[user] 降级到 Mock')
    return [...mockData]
  }
}

export async function create(data: Omit<User, 'id' | 'created_at' | 'updated_at'>) {
  console.warn('[user] 后端暂不支持后台新增用户，请通过注册接口创建用户')
  const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as User
  mockData.push(item)
  return item
}

export async function deleteById(id: number) {
  console.warn('[user] 后端暂不支持后台删除用户，请使用禁用状态代替')
  const idx = findIdx(mockData, id)
  if (idx !== -1) mockData.splice(idx, 1)
}

export async function toggleUserStatusById(id: number) {
  try {
    const user = (await getAll()).find(item => Number(item.id) === id)
    return await put(`/admin/users/${id}/status`, { status: user?.status === 'active' ? 'disabled' : 'active' })
  } catch {
    console.log('[user] toggleStatus 降级到 Mock')
    const user = mockData.find(item => Number(item.id) === id)
    if (user) user.status = user.status === 'active' ? 'disabled' : 'active'
  }
}

export async function updateUserProfileById(id: number, data: Partial<Pick<User, 'nickname' | 'password' | 'avatar' | 'username' | 'role'>>) {
  try {
    if (data.role) return await put<User>(`/admin/users/${id}/role`, { role: data.role })
    throw new Error('后端暂不支持后台修改用户基础资料')
  } catch {
    console.log('[user] updateProfile 降级到 Mock')
    const idx = findIdx(mockData, id)
    if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
  }
}
