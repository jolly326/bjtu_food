import { get } from './http'
import { pageRecords } from './adapter'

/**
 * 操作日志（task-10 Web · 操作日志 W4）。
 * 仅 GET /admin/operation-logs 只读查询（AOP 埋点写，前端不写）。
 * 后端出参 camelCase：OperationLogVO{ id, adminId, adminNickname, action, targetType, targetId, ip, createdAt }。
 */

export interface OperationLogVO {
  id: number
  adminId: number
  adminNickname: string
  action: string
  targetType: string
  targetId: number | null
  ip: string
  createdAt: string
}

function logToLegacy(raw: any): OperationLogVO {
  return {
    id: raw.id,
    adminId: raw.adminId ?? raw.admin_id ?? 0,
    adminNickname: raw.adminNickname || '',
    action: raw.action || '',
    targetType: raw.targetType ?? raw.target_type ?? '',
    targetId: raw.targetId ?? raw.target_id ?? null,
    ip: raw.ip || '',
    createdAt: raw.createdAt ?? raw.created_at ?? '',
  }
}

/** 操作日志列表（只读，多过滤维度） */
export async function listOperationLogs(params: {
  adminId?: number
  action?: string
  targetType?: string
  keyword?: string
  startAt?: string
  endAt?: string
  page?: number
  pageSize?: number
}): Promise<{ list: OperationLogVO[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.adminId) query.adminId = params.adminId
  if (params.action) query.action = params.action
  if (params.targetType) query.targetType = params.targetType
  if (params.keyword) query.keyword = params.keyword
  if (params.startAt) query.startAt = params.startAt
  if (params.endAt) query.endAt = params.endAt
  const data: any = await get('/admin/operation-logs', query)
  return {
    list: pageRecords(data).map(logToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}
