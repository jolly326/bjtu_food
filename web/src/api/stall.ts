/** 档口管理域（docs/api/web/stalls.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { Page, StallBody, StallOut } from './types'

export const stallApi = {
  list: (params?: { canteenId?: number; page?: number; size?: number }) =>
    http.get<Page<StallOut>>(`/admin/stalls${toQuery({ ...params })}`),
  create: (body: StallBody) => http.post<StallOut>('/admin/stalls', body),
  update: (id: number, body: StallBody) => http.put<StallOut>(`/admin/stalls/${id}`, body),
  remove: (id: number) => http.delete<null>(`/admin/stalls/${id}`),
}
