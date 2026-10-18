/** 橱窗域（docs/api/client/posters.md）。 */

import { http } from '@/utils/request'

import type { PosterOut } from './types'

export const posterApi = {
  /** 橱窗：已上架的海报（运营固定序，poster.sort 升序） */
  list: () => http.get<PosterOut[]>('/posters'),
}
