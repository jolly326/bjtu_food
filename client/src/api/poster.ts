/** 橱窗域（docs/api/client/posters.md）。 */

import { http } from '@/utils/request'

import type { PosterOut } from './types'

export const posterApi = {
  /** 橱窗：已上传海报的档口，随机顺序 */
  list: () => http.get<PosterOut[]>('/posters'),
}
