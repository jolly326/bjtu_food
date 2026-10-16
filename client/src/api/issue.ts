/** 菜品纠错域（docs/api/client/issues.md）。 */

import { http } from '@/utils/request'

import type { IssueOptionOut } from './types'

export const issueApi = {
  /** 纠错选项字典 */
  options: () => http.get<IssueOptionOut[]>('/dish-issue-options'),
  /** 提交纠错（只含一个选项） */
  submit: (dishId: number, optionId: number) => http.post<null>(`/dishes/${dishId}/issues`, { optionId }),
}
