import type { User } from '@/types'
import { get, put } from './http'
import { pageRecords, userToLegacy } from './adapter'

/**
 * 用户列表（受控分页，page+pageSize 透传后端；total 来自后端返回）。
 * pageSize 上限受后端 PageUtil 限制（≤100），不在此放宽。
 */
export async function listUsers(params: {
  status?: string
  page?: number
  pageSize?: number
} = {}): Promise<{ list: User[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.status) query.status = params.status
  // WEB-08：后端 GET /admin/users 不接收 keyword（模糊检索参数不存在），
  // 关键词搜索为前端本地过滤（UserView.filteredStudents），此处禁止透传该参数。
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

// getProfile / updateProfile（GET|PUT /auth/profile）不提供（WEB-05）：
// 端点属学生端 JWT 体系，管理端走 X-Admin-Token 口令、必然 401，且全仓零消费。

// updatePassword（PUT /auth/password）不提供（Q-108 / G-13 / spec §5.y.1）：
// 该端点后端已移除；学生侧无密码体系（user.password 恒 NULL），管理员语义亦随单口令模型失效。

/**
 * 切换用户状态：直接向后端传目标状态，不再前端 getAll() 全量拉取再反查（P-2 性能）。
 * 调用方（UserView）已知当前行 status，计算目标状态后传入：
 *   target = currentStatus === 'active' ? 'disabled' : 'active'
 */
export async function toggleUserStatusById(id: number, targetStatus: 'active' | 'disabled') {
  await put<void>(`/admin/users/${id}/status`, { status: targetStatus })
}
