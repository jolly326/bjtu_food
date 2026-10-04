import { get } from './http'
import type { DashboardVO } from '@/types/common'

/**
 * D1 运营看板（`GET /admin/dashboard`）。
 *
 * <p>契约真源：[D1-运营看板](../../../docs/api/web/dashboard.md)。
 * <p>**只读聚合、无参数、不分页**：一次请求返回三区全量（首屏不该为三个分区发三次请求）。
 * 本页不含任何写操作；所有处置都在各自页面（评价 / 反馈 / 举报 / 纠错 / 主数据）。
 */
export function getDashboard(): Promise<DashboardVO> {
  return get<DashboardVO>('/admin/dashboard')
}
