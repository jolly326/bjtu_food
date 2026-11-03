/** 菜品域（docs/api/client/dishes.md）。 */

import { http } from '@/utils/request'

import type { DishVO, RecommendOut } from './types'

export const dishApi = {
  /** 全量在售菜品（图标网格初始渲染） */
  list: () => http.get<DishVO[]>('/dishes'),
  /** 一句话匹配（硬约束 + 语义召回） */
  recommend: (query: string) => http.post<RecommendOut>('/dishes/recommend', { query }),
}
