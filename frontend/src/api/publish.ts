/**
 * 学生发布 / 我的发布 接口模块（替换原 stall-owner 红线残留）
 * 对齐 project_spec.md §3.x.5：POST /dishes、PUT /dishes/{id}、GET /my/dishes
 *
 * 金额：提交时由元转分（yuanToFen）；后端返回分，列表由 dish.ts 统一转元。
 */
import { post, put, get } from './http'
import type { MyPublishDish } from '@/types/dish'
import { yuanToFen } from '@/utils/money'

export interface DishPublishPayload {
  stallId: number
  name: string
  /** 前端填写「元」，提交前转分 */
  price: number
  description?: string
  images?: string[]
  tags?: string
}

/** 学生发布菜品 → 返回新菜品 id */
export async function publishDish(payload: DishPublishPayload): Promise<number> {
  return post<number>('/dishes', {
    ...payload,
    price: yuanToFen(payload.price),
  })
}

/** 学生编辑 / 重新提交（复用原记录，后端置 audit_status=pending） */
export async function updateMyDish(id: number, payload: DishPublishPayload): Promise<void> {
  await put(`/dishes/${id}`, {
    ...payload,
    price: yuanToFen(payload.price),
  })
}

/** 我的发布列表（含审核态与退回原因），可按 audit_status 过滤 */
export async function getMyDishes(auditStatus?: 'pending' | 'approved' | 'rejected'): Promise<MyPublishDish[]> {
  const params: Record<string, any> = {}
  if (auditStatus) params.auditStatus = auditStatus
  return get<MyPublishDish[]>('/my/dishes', params)
}
