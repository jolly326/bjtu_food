import { get, post } from './http'
import { pageRecords } from './adapter'

/**
 * 实体贡献审核中心（task-12.1 · Web 审核中心）。
 * 后端独立 apply_action 表，统一审核状态机：pending → approved | rejected（rejected 必填 rejectReason）。
 * 端点（ADMIN）：
 *   GET  /admin/apply?entityType=dish|stall|canteen&status=&action=   审核列表
 *   POST /admin/apply/{id}/approve                              通过（触发副作用：NEW→建实体 / CLOSE→置off / CHANGE→写回字段）
 *   POST /admin/apply/{id}/reject  { rejectReason }             退回（必填原因，回显学生端）
 */

export type ApplyEntityType = 'dish' | 'stall' | 'canteen'
export type ApplyType = 'NEW' | 'CLOSE' | 'CHANGE'
export type ApplyStatus = 'pending' | 'approved' | 'rejected'

/** apply_action 记录（camelCase，与后端 ApplyActionVO 对齐） */
export interface ApplyActionVO {
  id: number
  applicantId?: number
  applicantName?: string
  entityType: ApplyEntityType
  entityId?: number | null
  applyType: ApplyType
  status: ApplyStatus
  /** 新增/变更字段快照（JSON 字符串或对象） */
  payload?: any
  rejectReason?: string
  createdAt?: string
  updatedAt?: string
  handledBy?: number | null
  handledAt?: string | null
}

function applyToLegacy(raw: any): ApplyActionVO {
  return {
    id: raw.id,
    applicantId: raw.applicantId ?? raw.applicant_id,
    applicantName: raw.applicantName || raw.applicant_name || '',
    entityType: raw.entityType ?? raw.entity_type,
    entityId: raw.entityId ?? raw.entity_id ?? null,
    applyType: raw.applyType ?? raw.apply_type,
    status: raw.status || 'pending',
    payload: raw.payload,
    rejectReason: (raw.rejectReason ?? raw.reject_reason) || '',
    createdAt: raw.createdAt ?? raw.created_at,
    updatedAt: raw.updatedAt ?? raw.updated_at,
    handledBy: raw.handledBy ?? raw.handled_by ?? null,
    handledAt: raw.handledAt ?? raw.handled_at ?? null,
  }
}

export async function listApply(params: {
  /** 实体类型；不传则查询全部实体（菜品/档口/食堂） */
  entityType?: ApplyEntityType
  status?: ApplyStatus
  action?: ApplyType
  page?: number
  pageSize?: number
}): Promise<{ list: ApplyActionVO[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.entityType) query.entityType = params.entityType
  if (params.status) query.status = params.status
  if (params.action) query.action = params.action
  const data: any = await get('/admin/apply', query)
  return {
    list: pageRecords(data).map(applyToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

export async function approveApply(id: number) {
  await post<void>(`/admin/apply/${id}/approve`)
}

export async function rejectApply(id: number, rejectReason: string) {
  await post<void>(`/admin/apply/${id}/reject`, { rejectReason })
}
