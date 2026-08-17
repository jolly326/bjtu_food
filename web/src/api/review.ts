import type { Review } from '@/types'
import { del, get, put } from './http'
import { pageRecords, reviewToLegacy } from './adapter'

/**
 * 评价列表（受控分页，page+pageSize 透传后端；total 来自后端返回）。
 * 若传 userId 则按用户过滤；pageSize 上限受后端 PageUtil 限制（≤100），不在此放宽。
 */
export async function listReviews(params: {
  userId?: number
  page?: number
  pageSize?: number
} = {}): Promise<{ list: Review[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.userId != null) query.userId = params.userId
  const data: any = await get<any>('/admin/reviews', query)
  return {
    list: pageRecords(data).map(reviewToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

/**
 * 全量采集（聚合页统计 / 单用户行为聚合等需要完整集合的场景）。
 * 后端单页上限 100，按 page 循环拉取直到取完，逻辑仍返回完整数组，避免被静默截断。
 * 传 userId 时单用户评价量通常 ≤100，单页即取完；不传时循环翻页覆盖全部。
 */
export async function getAll(userId?: number): Promise<Review[]> {
  const all: Review[] = []
  let page = 1
  const pageSize = 100
  for (let guard = 0; guard < 1000; guard++) {
    const { list } = await listReviews({ userId, page, pageSize })
    if (!list.length) break
    all.push(...list)
    if (list.length < pageSize) break
    page++
  }
  return all
}

// 注意：评价由学生端提交（POST /reviews），后台仅审核 hide / delete，不提供 create。
export async function updateById(id: number, _data: Partial<Review>) {
  await put<void>(`/admin/reviews/${id}/hide`)
}

export async function deleteById(id: number) {
  await del<void>(`/admin/reviews/${id}`)
}
