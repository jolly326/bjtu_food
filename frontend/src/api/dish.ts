import { request } from './request'
import type { Dish, DishDetail, DishQuery } from '@/types/dish'

export function getRecommendList(): Promise<Dish[]> {
  // MVP mock
  return Promise.resolve([
    { id: 1, name: '红烧牛肉面', price: 15, image: '', rating: 4.8, ratingCount: 256, tags: ['招牌菜', '必吃推荐'], description: '浓汤慢炖，牛肉酥烂', canteen: '第一食堂', stallName: '面面俱到' },
    { id: 2, name: '黄焖鸡米饭', price: 14, image: '', rating: 4.6, ratingCount: 198, tags: ['招牌菜'], description: '鲜嫩多汁，下饭首选', canteen: '第一食堂', stallName: '黄焖世家' },
    { id: 3, name: '麻辣香锅', price: 18, image: '', rating: 4.7, ratingCount: 312, tags: ['必吃推荐'], description: '自选食材，随心搭配', canteen: '第二食堂', stallName: '麻辣诱惑' },
    { id: 4, name: '照烧鸡排饭', price: 16, image: '', rating: 4.5, ratingCount: 167, tags: ['招牌菜'], description: '日式照烧酱汁，外焦里嫩', canteen: '第三食堂', stallName: '烧肉工坊' },
    { id: 5, name: '酸菜鱼', price: 20, image: '', rating: 4.9, ratingCount: 423, tags: ['招牌菜', '必吃推荐'], description: '活鱼现杀，酸爽开胃', canteen: '第二食堂', stallName: '渔味轩' },
    { id: 6, name: '西红柿鸡蛋面', price: 10, image: '', rating: 4.3, ratingCount: 89, tags: [], description: '家常味道，温暖胃', canteen: '第一食堂', stallName: '面面俱到' },
    { id: 7, name: '宫保鸡丁', price: 14, image: '', rating: 4.4, ratingCount: 134, tags: ['必吃推荐'], description: '酸甜微辣，经典川菜', canteen: '第二食堂', stallName: '川味轩' },
    { id: 8, name: '牛肉拌面', price: 12, image: '', rating: 4.2, ratingCount: 76, tags: [], description: '清爽拌面，夏日首选', canteen: '第三食堂', stallName: '面面俱到' },
  ])
}

/**
 * 获取指定食堂下指定档口的菜品列表
 */
export function getStallDishes(canteen: string, stallName: string): Promise<Dish[]> {
  return getRecommendList().then(list =>
    list.filter(d => d.canteen === canteen && d.stallName === stallName)
  )
}

export function searchDishes(query: DishQuery): Promise<Dish[]> {
  return getRecommendList().then(list => {
    let result = [...list]
    if (query.keyword) {
      const kw = query.keyword.toLowerCase()
      result = result.filter(d => d.name.includes(kw) || d.stallName.includes(kw))
    }
    if (query.canteen) {
      result = result.filter(d => d.canteen === query.canteen)
    }
    if (query.tags && query.tags.length > 0) {
      result = result.filter(d => query.tags!.some(t => d.tags.includes(t)))
    }
    if (query.minPrice !== undefined) {
      result = result.filter(d => d.price >= query.minPrice!)
    }
    if (query.maxPrice !== undefined) {
      result = result.filter(d => d.price <= query.maxPrice!)
    }
    if (query.sortBy === 'rating') {
      result.sort((a, b) => b.rating - a.rating)
    } else if (query.sortBy === 'price') {
      result.sort((a, b) => a.price - b.price)
    }
    return result
  })
}

/** 食堂信息 */
export interface CanteenInfo {
  name: string
  location: string
  icon: string
}

export function getCanteenList(): Promise<CanteenInfo[]> {
  return Promise.resolve([
    { name: '第一食堂', location: '一食堂一层', icon: '🍜' },
    { name: '第二食堂', location: '二食堂一层', icon: '🍛' },
    { name: '第三食堂', location: '三食堂一层', icon: '🥗' },
  ])
}

export function getDishDetail(id: number): Promise<DishDetail> {
  return getRecommendList().then(list => {
    const dish = list.find(d => d.id === id)
    if (!dish) throw new Error('菜品不存在')
    return {
      ...dish,
      ratingDistribution: [
        { star: 5, count: 156 },
        { star: 4, count: 68 },
        { star: 3, count: 22 },
        { star: 2, count: 8 },
        { star: 1, count: 2 },
      ],
    }
  })
}
