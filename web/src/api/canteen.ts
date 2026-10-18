/** 食堂管理域（docs/api/web/canteens.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { CanteenOut, Page } from './types'

export const canteenApi = {
  list: (page = 1, pageSize = 20) =>
    http.get<Page<CanteenOut>>(`/admin/canteens${toQuery({ page, pageSize })}`),
  create: (name: string) => http.post<CanteenOut>('/admin/canteens', { name }),
  update: (id: number, name: string) => http.put<CanteenOut>(`/admin/canteens/${id}`, { name }),
  remove: (id: number) => http.delete<null>(`/admin/canteens/${id}`),
}
