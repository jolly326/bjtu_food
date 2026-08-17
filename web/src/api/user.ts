import type { User } from '@/types'
import { get, post, put } from './http'
import { pageRecords, userToLegacy } from './adapter'

export async function login(username: string, password: string): Promise<{ token: string; username: string }> {
  return await post('/auth/login', { account: username, password })
}

/**
 * 用户列表（受控分页，page+pageSize 透传后端；total 来自后端返回）。
 * pageSize 上限受后端 PageUtil 限制（≤100），不在此放宽。
 */
export async function listUsers(params: {
  status?: string
  keyword?: string
  page?: number
  pageSize?: number
} = {}): Promise<{ list: User[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.status) query.status = params.status
  if (params.keyword) query.keyword = params.keyword
  const data: any = await get('/admin/users', query)
  return {
    list: pageRecords(data).map(userToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

/**
 * 全量采集（用于聚合页统计计数 / 账号下拉等需要完整集合的场景）。
 * 后端单页上限 100，这里按 page 循环拉取直到取完，逻辑仍返回完整数组，
 * 不依赖「单页拉全量」，也不会被静默截断。
 */
export async function getAll(): Promise<User[]> {
  const all: User[] = []
  let page = 1
  const pageSize = 100
  // 上限保护：避免后端契约异常时无限循环（理论上用户量远小于此）
  for (let guard = 0; guard < 1000; guard++) {
    const { list } = await listUsers({ page, pageSize })
    if (!list.length) break
    all.push(...list)
    if (list.length < pageSize) break
    page++
  }
  return all
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
