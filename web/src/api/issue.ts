/** 纠错处置域（docs/api/web/issues.md）。 */

import { http } from './request'
import { toQuery } from './query'
import type { IssueOut, Page } from './types'

export const issueApi = {
  list: (params?: { handled?: boolean; dishId?: number; page?: number; pageSize?: number }) =>
    http.get<Page<IssueOut>>(`/admin/dish-issues${toQuery({ ...params })}`),
  setHandled: (id: number, handled: boolean) =>
    http.patch<IssueOut>(`/admin/dish-issues/${id}`, { handled }),
}
