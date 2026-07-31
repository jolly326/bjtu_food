/**
 * 实体贡献统一申请接口（task-12.1，STU 统一入口）
 * 对应后端 apply_action 表 + 统一审核状态机：
 *  - POST /my/apply        统一提交新增/下架/变更申请
 *  - GET  /my/submissions   聚合本人 apply_action + 本人 moment（两标签）
 */
import { post, get } from './http'
import type { AuditStatus } from '@/types/moment'

/** 申请实体类型（对齐 spec entityType） */
export type ApplyEntityType = 'DISH' | 'STALL' | 'CANTEEN'

/** 申请动作类型（对齐 spec applyType） */
export type ApplyType = 'NEW' | 'CLOSE' | 'CHANGE'

/** 提交申请请求体 */
export interface ApplySubmit {
  entityType: ApplyEntityType
  applyType: ApplyType
  /** 下架/变更类必填；新增类可空 */
  entityId?: number | null
  /** 新增/变更字段快照（JSON 字符串或对象） */
  payload?: Record<string, any>
}

/** 我的提交聚合项 */
export interface SubmissionVO {
  /** apply=实体申请 / moment=动态 */
  type: 'apply' | 'moment'
  id: number
  /** 实体申请时的 entityType（DISH/STALL/CANTEEN） */
  entityType?: ApplyEntityType
  /** 申请动作（NEW/CLOSE/CHANGE） */
  action?: ApplyType
  /** 预览标题（实体申请取 payload 预览；动态取内容摘要） */
  title: string
  /** 审核状态 */
  status: AuditStatus
  /** 动态类标记下架状态（0正常/1下架） */
  off?: boolean
  createdAt?: string
}

/** 统一提交申请（POST /my/apply） */
export async function submitApply(payload: ApplySubmit): Promise<{ id: number }> {
  return await post<{ id: number }>('/my/apply', payload)
}

/** 我的提交聚合（GET /my/submissions） */
export async function getMySubmissions(): Promise<SubmissionVO[]> {
  const res = await get<any>('/my/submissions')
  if (Array.isArray(res)) return res as SubmissionVO[]
  if (Array.isArray(res?.list)) return res.list as SubmissionVO[]
  if (Array.isArray(res?.records)) return res.records as SubmissionVO[]
  return []
}
