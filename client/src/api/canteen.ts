/** 食堂筛选域（docs/api/client/canteens.md）。 */

import { http } from '@/utils/request'

import type { CanteenOut } from './types'

export const canteenApi = {
  /** 食堂字典（美味 tab 右上角筛选） */
  list: () => http.get<CanteenOut[]>('/canteens'),
}
