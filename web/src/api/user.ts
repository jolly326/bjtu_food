import type { User } from '@/types'
import { nextId, findIdx } from './common'
import { get, post, put, del } from './http'

const mockData: User[] = [
  { id: 100 as unknown as bigint, username: '1', password: '1', nickname: '管理员', avatar: '', role: 'admin', status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 1 as unknown as bigint, username: 'zhangsan', password: '123456', nickname: '张三', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 2 as unknown as bigint, username: 'lisi', password: '123456', nickname: '李四', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-01-05'), updated_at: new Date('2024-01-05') },
  { id: 3 as unknown as bigint, username: 'wangwu', password: '123456', nickname: '王五', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 4 as unknown as bigint, username: 'zhaoliu', password: '123456', nickname: '赵六', avatar: '', role: 'user', status: 'disabled', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 5 as unknown as bigint, username: 'sunqi', password: '123456', nickname: '孙七', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-02-15'), updated_at: new Date('2024-02-15') },
]

export async function login(username: string, password: string): Promise<{ token: string; username: string }> {
  return await post('/auth/login', { username, password })
}
export async function getAll(): Promise<User[]> {
  try { return await get<User[]>('/users') }
  catch { console.log('[user] 降级到 Mock'); return [...mockData] }
}
export async function create(data: Omit<User, 'id' | 'created_at' | 'updated_at'>) {
  try { return await post<User>('/users', data) }
  catch { console.log('[user] create 降级到 Mock'); const item = { ...data, id: nextId(), created_at: new Date(), updated_at: new Date() } as User; mockData.push(item); return item }
}
export async function deleteById(id: number) {
  try { return await del(`/users/${id}`) }
  catch { console.log('[user] delete 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) mockData.splice(idx, 1) }
}
export async function toggleUserStatusById(id: number) {
  try { return await put(`/users/${id}/toggle-status`) }
  catch { console.log('[user] toggleStatus 降级到 Mock'); const u = mockData.find(u => Number(u.id) === id); if (u) u.status = u.status === 'active' ? 'disabled' : 'active' }
}
export async function updateUserProfileById(id: number, data: Partial<Pick<User, 'nickname' | 'password' | 'avatar' | 'username'>>) {
  try { return await put<User>(`/users/${id}`, data) }
  catch { console.log('[user] updateProfile 降级到 Mock'); const idx = findIdx(mockData, id); if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() }) }
}
