import type { User } from '@/types'

function findIdx(arr: User[], id: number) { return arr.findIndex(u => Number(u.id) === id) }

const mockData: User[] = [
  { id: 1 as unknown as bigint, username: 'zhangsan', password: '123456', nickname: '张三', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 2 as unknown as bigint, username: 'lisi', password: '123456', nickname: '李四', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-01-05'), updated_at: new Date('2024-01-05') },
  { id: 3 as unknown as bigint, username: 'wangwu', password: '123456', nickname: '王五', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-01-10'), updated_at: new Date('2024-01-10') },
  { id: 4 as unknown as bigint, username: 'zhaoliu', password: '123456', nickname: '赵六', avatar: '', role: 'user', status: 'disabled', created_at: new Date('2024-02-01'), updated_at: new Date('2024-02-01') },
  { id: 5 as unknown as bigint, username: 'sunqi', password: '123456', nickname: '孙七', avatar: '', role: 'user', status: 'active', created_at: new Date('2024-02-15'), updated_at: new Date('2024-02-15') },
  { id: 100 as unknown as bigint, username: 'admin', password: 'admin123', nickname: '超级管理员', avatar: '', role: 'super_admin', status: 'active', created_at: new Date('2024-01-01'), updated_at: new Date('2024-01-01') },
  { id: 101 as unknown as bigint, username: 'manager01', password: 'abc123', nickname: '管理员小王', avatar: '', role: 'admin', status: 'active', created_at: new Date('2024-03-01'), updated_at: new Date('2024-03-01') },
  { id: 102 as unknown as bigint, username: 'manager02', password: 'abc456', nickname: '管理员小李', avatar: '', role: 'admin', status: 'disabled', created_at: new Date('2024-04-01'), updated_at: new Date('2024-04-01') },
]

export function getAll() { return mockData }
export function toggleUserStatusById(id: number) {
  const u = mockData.find(u => Number(u.id) === id)
  if (u) u.status = u.status === 'active' ? 'disabled' : 'active'
}
export function updateUserProfileById(id: number, data: Partial<Pick<User, 'nickname' | 'password'>>) {
  const idx = findIdx(mockData, id)
  if (idx !== -1) Object.assign(mockData[idx]!, data, { updated_at: new Date() })
}
