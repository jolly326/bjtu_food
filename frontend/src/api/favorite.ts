import { request } from './request'
import type { Dish } from '@/types/dish'

export function getFavoriteList(): Promise<Dish[]> {
  return Promise.resolve([
    { id: 1, name: '红烧牛肉面', price: 15, image: '', rating: 4.8, ratingCount: 256, tags: ['招牌菜', '必吃推荐'], description: '浓汤慢炖，牛肉酥烂', canteen: '第一食堂', stallName: '面面俱到' },
    { id: 5, name: '酸菜鱼', price: 20, image: '', rating: 4.9, ratingCount: 423, tags: ['招牌菜', '必吃推荐'], description: '活鱼现杀，酸爽开胃', canteen: '第二食堂', stallName: '渔味轩' },
  ])
}

export function addFavorite(dishId: number): Promise<void> {
  return Promise.resolve()
}

export function removeFavorite(dishId: number): Promise<void> {
  return Promise.resolve()
}
