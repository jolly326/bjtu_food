/** 美食地图域（docs/api/client/dishes.md）。 */

import { http } from '@/utils/request'

import type { DishLikeOut, DishVO } from './types'

export const dishApi = {
  /** 美食地图：全量在售菜品（随机散布由端上完成） */
  list: () => http.get<DishVO[]>('/dishes'),
  /** 两态点赞 toggle：已赞 ⇒ 取消，未赞 ⇒ 点赞 */
  like: (dishId: number) => http.post<DishLikeOut>(`/dishes/${dishId}/likes`),
}
