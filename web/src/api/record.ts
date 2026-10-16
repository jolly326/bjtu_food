/** 评分数据域（docs/api/web/records.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { Page, RecordOut } from './types'

export const recordApi = {
  list: (params?: { dishId?: number; reaction?: string; page?: number; size?: number }) =>
    http.get<Page<RecordOut>>(`/admin/dish-records${toQuery({ ...params })}`),
}
