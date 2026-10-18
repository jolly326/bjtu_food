/** 记忆域（docs/api/client/records.md）。 */

import { http } from '@/utils/request'

import type { RecordOut } from './types'

export const recordApi = {
  /** 记忆清单（全量，按 wantedAt 降序） */
  list: () => http.get<RecordOut[]>('/my/records'),
  /** 去品尝（幂等） */
  want: (dishId: number) => http.post<RecordOut>('/my/records', { dishId }),
  /** 三态评分（5 喜欢 / 3 一般 / 1 不喜欢 / **null 清除评价**；循环可改） */
  react: (id: number, reaction: number | null) =>
    http.patch<RecordOut>(`/my/records/${id}`, { reaction }),
  /** 取消品尝（仅未评分） */
  cancel: (id: number) => http.delete<null>(`/my/records/${id}`),
}
