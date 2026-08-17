/**
 * 实体贡献统一申请接口（task-12.1，STU 统一入口）
 * 对应后端 apply_action 表 + 统一审核状态机：
 *  - POST /my/apply        统一提交新增/下架/变更申请
 *  - GET  /my/submissions   聚合本人 apply_action + 本人 moment（两标签）
 */
import { post } from './http'

/** 申请实体类型（对齐 spec entityType） */
export type ApplyEntityType = 'DISH' | 'STALL' | 'CANTEEN'

/** 申请动作类型（对齐 spec applyType） */
export type ApplyAction = 'NEW' | 'CLOSE' | 'CHANGE'

/** 提交申请请求体 */
interface ApplySubmit {
  entityType: ApplyEntityType
  applyType: ApplyAction
  /** 下架/变更类必填；新增类可空 */
  entityId?: number | null
  /** 新增/变更字段快照（JSON 字符串或对象） */
  payload?: Record<string, any>
}

/** 统一提交申请（POST /my/apply） */
export async function submitApply(payload: ApplySubmit): Promise<{ id: number }> {
  return await post<{ id: number }>('/my/apply', payload)
}


