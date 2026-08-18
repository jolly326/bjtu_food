import { get } from './http'

/**
 * 工作台（DashboardView 数据源，契约见 docs/project_spec.md §0.4.1）。
 * GET /admin/dashboard?range=week
 *   - 后端一次返回：待办 count + 待办明细（申请/动态/反馈各 5 条）、8 项规模指标、近期操作（日志 10 条）。
 *   - 出参 camelCase，与后端 DashboardVO 一一对应；range 当前固定 week（DashboardView 未使用 range 分档）。
 *   - 注：后端 DashboardVO 另含 newDishCount/newReviewCount/hotCanteens/hotDishes/viewTrend/reviewTrend
 *     （供后续图表看板复用），但 DashboardView 当前不消费这些图表字段。
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
