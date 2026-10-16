/** 推荐菜品域（docs/api/client/dishes.md）。 */

import { http } from '@/utils/request'

import { toQuery } from './query'
import type { DishCardOut } from './types'

export const dishApi = {
  /** 美食：推荐卡片（加权随机 + wanted 态） */
  list: (params?: { canteenId?: number; pageSize?: number }) =>
    http.get<{ records: DishCardOut[] }>(
      '/dishes' + toQuery({ canteenId: params?.canteenId, pageSize: params?.pageSize }),
    ),
}
