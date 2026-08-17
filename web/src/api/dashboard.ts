import { get } from './http'

/**
 * 数据看板（task-12 W2）。
 * GET /admin/dashboard?range=week|month|all
 *   - week / month：近 7 天 / 近 30 天（按后端 StatsController 口径）
 *   - all：后端映射为「近 30 天」（与 week/month 的 month 同口径，非全量历史）
 * 后端出参 camelCase：DashboardVO{ range, newDishCount, newReviewCount, totalDishCount,
 * totalReviewCount, hotCanteens[{id,name,score}], hotDishes[{id,name,score}],
 * viewTrend{dates[],values[]}, reviewTrend{dates[],values[]} }。
 */

export interface DashboardRankItem {
  id: number
  name: string
  score: number
}

export interface DashboardTrend {
  dates: string[]
  values: number[]
}

export interface DashboardTodoItem {
  id: number
  title: string
  type: string
  time: string
}

export interface DashboardRecentLog {
  id: number
  operator: string
  action: string
  target: string
  time: string
}

export interface DashboardData {
  range: string
  newDishCount: number
  newReviewCount: number
  totalDishCount: number
  totalReviewCount: number
  totalCanteenCount: number
  totalStallCount: number
  totalUserCount: number
  totalMomentCount: number
  totalApplyCount: number
  totalFeedbackCount: number
  pendingApplyCount: number
  pendingMomentCount: number
  pendingFeedbackCount: number
  pendingApplies: DashboardTodoItem[]
  pendingMoments: DashboardTodoItem[]
  pendingFeedbacks: DashboardTodoItem[]
  recentLogs: DashboardRecentLog[]
  hotCanteens: DashboardRankItem[]
  hotDishes: DashboardRankItem[]
  viewTrend: DashboardTrend
  reviewTrend: DashboardTrend
}

export type DashboardRange = 'week' | 'month' | 'all'

/** 数据看板总览 */
export async function getDashboard(range: DashboardRange = 'week'): Promise<DashboardData> {
  return await get<DashboardData>('/admin/dashboard', { range })
}
