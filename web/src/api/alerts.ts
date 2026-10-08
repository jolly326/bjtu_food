/**
 * 管理端安全告警接口。
 *
 * <p><b>契约唯一真源</b>：[api/web/alerts.md](../../../docs/api/web/alerts.md)，
 * 本文件不复述字段语义，只做类型映射与路径收敛。
 *
 * <p><b>只读</b>：告警记录只追加 —— 面板没有写入口（能改告警的人就能抹掉自己的痕迹）。
 */
import { getAdminPage } from './http'
import type { AdminPage, AlertListParams, SecurityAlertVO } from '@/types/common'

/** 分页查询安全告警记录（按发生时间倒序） */
export function listAlerts(params: AlertListParams): Promise<AdminPage<SecurityAlertVO>> {
  return getAdminPage<SecurityAlertVO>('/admin/alerts', params)
}
