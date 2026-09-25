import type { Review } from '@/types'
import { del, get, put } from './http'
import { pageRecords, reviewToLegacy } from './adapter'

/**
 * 评价列表（受控分页，page+pageSize 透传后端；total 来自后端返回）。
 * 若传 userId 则按用户过滤；pageSize 上限受后端 PageUtil 限制（≤100），不在此放宽。
 *
 * isHidden（可选）：后端 ReviewAdminController 的 isHidden 为 `Integer`（`.eq(isHidden != null, …)`），
 * 故此处收窄为 0 | 1 传数值——传布尔会被序列化成 `isHidden=true` 触发后端类型转换失败（400）。
 * 用途：按显隐 / 关键词 / 用户过滤的受控分页查询（pageSize=1 时只取 total，可用于计数）。
 *
 * 无「安检状态」查询参数（取消人工复核：放行态与待复核态
 * 均对客户端放行、仅风险项拒绝，后台不再消费该字段，后端字段同源移除）。
 */
export async function listReviews(params: {
  userId?: number
  keyword?: string
  isHidden?: 0 | 1
  page?: number
  pageSize?: number
} = {}): Promise<{ list: Review[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.userId != null) query.userId = params.userId
  if (params.keyword) query.keyword = params.keyword
  if (params.isHidden !== undefined) query.isHidden = params.isHidden
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
// 后台隐藏/显示走 PUT /admin/reviews/{id}/hide，body 需带 hidden 字段；
// 否则后端 setHidden 因 body 为 null 默认 hidden=false，导致「隐藏」永远被还原为「显示」。
// 这里把 Review 的 is_hidden 透传为 hidden，修复评价显隐失效（web-review-restore P0）。
// is_hidden 允许 number | boolean，兼容详情页 store.updateReview(id, { is_hidden: boolean })。
// 注：Omit 掉 Review.is_hidden（number）再交叉，避免 intersection 收窄回 number 导致 boolean 调用方报错。
export async function updateById(id: number, data: Omit<Partial<Review>, 'is_hidden'> & { is_hidden?: number | boolean }) {
  const raw = data.is_hidden as number | boolean | undefined
  const hidden = raw === 1 || raw === true
  await put<void>(`/admin/reviews/${id}/hide`, { hidden })
}

export async function deleteById(id: number) {
  await del<void>(`/admin/reviews/${id}`)
}

/**
 * 评价全量检索：后端按 isHidden + 服务端过滤分页，前端翻页聚合全部页，
 * 避免默认分页硬上限导致超出部分漏搜（如关键词检索场景）。
 * isHidden 取 0 | 1（与 listReviews 同源：后端字段为 Integer，布尔会导致类型转换失败 400）。
 *
 * 本函数（唯一仍被消费的成员）迁入 review 模块——它只打 `/admin/reviews`，归属评价域。
 * 无「安检状态」入参（人工复核已取消）。
 */
export async function listAllReviews(isHidden?: 0 | 1, keyword?: string): Promise<Review[]> {
  const PAGE_SIZE = 100
  const all: Review[] = []
  let page = 1
  for (;;) {
    const params: Record<string, unknown> = { page, pageSize: PAGE_SIZE }
    if (isHidden !== undefined) params.isHidden = isHidden
    if (keyword) params.keyword = keyword.trim()
    const data = await get<any>('/admin/reviews', params)
    const records: Review[] = pageRecords(data).map(reviewToLegacy)
    all.push(...records)
    const total: number = Array.isArray(data) ? records.length : (data?.total ?? records.length)
    if (records.length === 0 || all.length >= total) break
    page += 1
  }
  return all
}
