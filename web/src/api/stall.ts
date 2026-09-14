/**
 * 档口 API（2026-09-14 Q-113/Q-115 拍板）：档口是**菜品筛选属性字典**，非业务实体。
 * 生命周期只有「新增 / 改名」——**不提供删除**（后端 DELETE 端点同步下线），
 * 故此处不导出 deleteById，杜绝删除调用残影（全仓 grep 应为 0）。
 */
import type { Stall } from '@/types'
import { get, post, put } from './http'
import { stallToApi, stallToLegacy } from './adapter'

export async function getAll(): Promise<Stall[]> {
  return (await get<any[]>('/admin/stalls')).map(stallToLegacy)
}

export async function create(data: Omit<Stall, 'id' | 'created_at' | 'updated_at'>) {
  await post<void>('/admin/stalls', stallToApi(data))
}

/** 改名（属性字典唯一可用的编辑动作） */
export async function updateById(id: number, data: Partial<Stall>) {
  await put<void>(`/admin/stalls/${id}`, stallToApi(data))
}
