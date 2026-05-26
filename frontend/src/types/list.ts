import type { Dish } from './dish'

export interface FoodList {
  id: number
  name: string
  description: string
  dishes: Dish[]
  userId: number
  shareCount: number
  createTime: string
}

export interface ListCreate {
  name: string
  description: string
  dishIds: number[]
}
