/** 纠错选项字典域（docs/api/web/issues.md）。 */

import { http } from './request'
import type { OptionOut } from './types'

export const optionApi = {
  list: () => http.get<OptionOut[]>('/admin/dish-issue-options'),
  create: (name: string) => http.post<OptionOut>('/admin/dish-issue-options', { name }),
  update: (id: number, name: string) =>
    http.put<OptionOut>(`/admin/dish-issue-options/${id}`, { name }),
  remove: (id: number) => http.delete<null>(`/admin/dish-issue-options/${id}`),
}
