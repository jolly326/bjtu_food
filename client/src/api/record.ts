/** 我的食谱域（docs/api/client/records.md）。 */

import { http } from '@/utils/request'

import type { RecordOut } from './types'

export const recordApi = {
  /** 记忆：我的食谱（全量，按 wantedAt 降序） */
  list: () => http.get<RecordOut[]>('/my/records'),
  /** 去品尝（幂等） */
  want: (dishId: number) => http.post<RecordOut>('/my/records', { dishId }),
  /** 三态评分（5 喜欢 / 3 一般 / 1 不喜欢；一次性） */
  react: (id: number, reaction: number) => http.patch<RecordOut>(`/my/records/${id}`, { reaction }),
  /** 取消品尝（仅未评分） */
  cancel: (id: number) => http.delete<null>(`/my/records/${id}`),
}
