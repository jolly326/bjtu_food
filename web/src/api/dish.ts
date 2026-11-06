/** 菜品管理域（docs/api/web/dishes.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { DishBody, DishOut, Page } from './types'

export const dishApi = {
  list: (params?: {
    stallId?: number
    canteenId?: number
    /** 上架状态过滤：true 在售 / false 已下架 */
    activate?: boolean
    /** 名称模糊匹配 */
    name?: string
    page?: number
    pageSize?: number
  }) => http.get<Page<DishOut>>(`/admin/dishes${toQuery({ ...params })}`),
  create: (body: DishBody) => http.post<DishOut>('/admin/dishes', body),
  update: (id: number, body: DishBody) => http.put<DishOut>(`/admin/dishes/${id}`, body),
  remove: (id: number) => http.delete<null>(`/admin/dishes/${id}`),
}
