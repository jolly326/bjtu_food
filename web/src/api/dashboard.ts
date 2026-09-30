import { get } from './http'
import type { DashboardVO } from '@/types/common'

/** B-02 运营看板：登录后首屏聚合指标（只读）。 */
export function getDashboard(): Promise<DashboardVO> {
  return get<DashboardVO>('/admin/dashboard')
}
