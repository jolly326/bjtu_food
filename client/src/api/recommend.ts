/**
 * 推荐接口模块（project_spec.md §3.x.4 / §3.x.5：GET /dishes/recommend）
 *
 * 公开接口，无需登录；登录态带 token 时个性化更强。
 * 返回 PageResult<DishVO>，前端转元后返回 Dish[]。
 */
import { get } from './http'
import type { Dish } from '@/types/dish'
import { toDish } from './dish'
import { listOf, totalOf, type PageResult, type RawRow } from './shared'

export async function getRecommendDishes(params?: {
  page?: number
  pageSize?: number
  excludeIds?: number[]
}): Promise<{ list: Dish[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params?.page ?? 1,
    pageSize: params?.pageSize ?? 10,
  }
  if (params?.excludeIds?.length) {
    query.excludeIds = params.excludeIds.join(',')
  }
  const res = await get<PageResult<RawRow>>('/dishes/recommend', query)
  const list = listOf(res).map(toDish)
  return {
    list,
    total: totalOf(res),
  }
}
