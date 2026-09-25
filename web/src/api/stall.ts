/**
 * 档口 API（Q-113/Q-115 拍板；蓝图 v1 §7.23 第 1 条再收紧）：
 * 档口是**菜品筛选属性字典**，非业务实体。生命周期只有「按名 upsert（随菜品，后端自动建档）/ 改名」
 * ——**不提供删除**（独立新增端点 POST /admin/stalls 不提供，后端 CanteenAdminController），
 * 故此处不导出 create，杜绝失效调用残影（全仓 grep 应为 0）。
 */
import type { Stall } from '@/types'
import { get, put } from './http'
import { stallToApi, stallToLegacy } from './adapter'

export async function getAll(): Promise<Stall[]> {
  return (await get<any[]>('/admin/stalls')).map(stallToLegacy)
}

/** 改名（属性字典唯一可用的编辑动作；新增一律走菜品按名 upsert） */
export async function updateById(id: number, data: Partial<Stall>) {
  await put<void>(`/admin/stalls/${id}`, stallToApi(data))
}
