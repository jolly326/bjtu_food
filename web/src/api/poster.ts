/** 海报管理域（docs/api/web/posters.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { Page, PosterBody, PosterOut } from './types'

export const posterApi = {
  list: (params?: {
    stallId?: number
    canteenId?: number
    status?: string
    page?: number
    pageSize?: number
  }) => http.get<Page<PosterOut>>(`/admin/posters${toQuery({ ...params })}`),
  create: (body: PosterBody) => http.post<PosterOut>('/admin/posters', body),
  update: (id: number, body: PosterBody) => http.put<PosterOut>(`/admin/posters/${id}`, body),
  remove: (id: number) => http.delete<null>(`/admin/posters/${id}`),
}
