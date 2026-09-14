import { get } from './http'

/**
 * 工作台（DashboardView 数据源，契约见 docs/project_spec.md §0.4.1）。
 * GET /admin/dashboard?range=week
 *   - 后端一次返回：待办 count + 待办明细（反馈 5 条）、5 项规模指标、近期操作（日志 10 条）。
 *   - 出参 camelCase，与后端 DashboardVO 一一对应；range 当前固定 week（DashboardView 未使用 range 分档）。
 *   - 注：apply 申请相关待办/指标已随 apply 全链路下线移除（见 change prelaunch-loop-closure）。
 *   - 2026-09-14（Q-106 / P2-08）：图表/趋势字段声明已移除——`newDishCount`/`newReviewCount`/
 *     `hotCanteens`/`hotDishes`/`viewTrend`/`reviewTrend` 前端从不消费且与 §0.4.1「工作台不含图表看板」
 *     一致，后端亦将同步从 DashboardVO 摘除；前端保留声明只会让契约看起来「有一半没实现」。
 */

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
  totalDishCount: number
  totalReviewCount: number
  totalCanteenCount: number
  totalStallCount: number
  totalUserCount: number
  totalFeedbackCount: number
  pendingFeedbackCount: number
  /** 待办明细（待处理反馈最近 5 条，按时间倒序） */
  pendingFeedbacks: DashboardTodoItem[]
  /** 近期操作（操作日志最近 10 条） */
  recentLogs: DashboardRecentLog[]
}

export type DashboardRange = 'week' | 'month' | 'all'

/** 数据看板总览 */
export async function getDashboard(range: DashboardRange = 'week'): Promise<DashboardData> {
  return await get<DashboardData>('/admin/dashboard', { range })
}
