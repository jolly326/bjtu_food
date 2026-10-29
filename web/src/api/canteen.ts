/** 食堂管理域（docs/api/web/canteens.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { CanteenOut, Page } from './types'

/** 食堂保存体（docs/api/web/canteens.md：`{ name, activate? }`） */
export type CanteenBody = { name: string; activate?: boolean }

export const canteenApi = {
  list: (page = 1, pageSize = 20) =>
    http.get<Page<CanteenOut>>(`/admin/canteens${toQuery({ page, pageSize })}`),
  create: (body: CanteenBody) => http.post<CanteenOut>('/admin/canteens', body),
  update: (id: number, body: CanteenBody) => http.put<CanteenOut>(`/admin/canteens/${id}`, body),
  remove: (id: number) => http.delete<null>(`/admin/canteens/${id}`),
}
