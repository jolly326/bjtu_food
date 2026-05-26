import type { Dish, DishDetail, DishQuery } from '@/types/dish'
import type { BannerItem, CanteenInfo, StallDetail } from '@/stores/types'

/**
 * 占位图路径 —— 指向 frontend/src/static/dish_placeholder.jpg
 * uni-app 会将 src/static/ 编译到 /static/ 下
 */
const PLACEHOLDER_IMG = '/static/dish_placeholder.jpg'

export function getRecommendList(): Promise<Dish[]> {
  // MVP mock —— 后期切换到真实 API: GET /api/dishes/hot
  return Promise.resolve([
    { id: 1, name: '红烧牛肉面', price: 15, image: PLACEHOLDER_IMG, rating: 4.8, ratingCount: 256, tags: ['招牌菜', '必吃推荐'], description: '浓汤慢炖，牛肉酥烂', canteen: '第一食堂', stallName: '面面俱到' },
    { id: 2, name: '黄焖鸡米饭', price: 14, image: PLACEHOLDER_IMG, rating: 4.6, ratingCount: 198, tags: ['招牌菜'], description: '鲜嫩多汁，下饭首选', canteen: '第一食堂', stallName: '黄焖世家' },
    { id: 3, name: '麻辣香锅', price: 18, image: PLACEHOLDER_IMG, rating: 4.7, ratingCount: 312, tags: ['必吃推荐'], description: '自选食材，随心搭配', canteen: '第二食堂', stallName: '麻辣诱惑' },
    { id: 4, name: '照烧鸡排饭', price: 16, image: PLACEHOLDER_IMG, rating: 4.5, ratingCount: 167, tags: ['招牌菜'], description: '日式照烧酱汁，外焦里嫩', canteen: '第三食堂', stallName: '烧肉工坊' },
    { id: 5, name: '酸菜鱼', price: 20, image: PLACEHOLDER_IMG, rating: 4.9, ratingCount: 423, tags: ['招牌菜', '必吃推荐'], description: '活鱼现杀，酸爽开胃', canteen: '第二食堂', stallName: '渔味轩' },
    { id: 6, name: '西红柿鸡蛋面', price: 10, image: PLACEHOLDER_IMG, rating: 4.3, ratingCount: 89, tags: [], description: '家常味道，温暖胃', canteen: '第一食堂', stallName: '面面俱到' },
    { id: 7, name: '宫保鸡丁', price: 14, image: PLACEHOLDER_IMG, rating: 4.4, ratingCount: 134, tags: ['必吃推荐'], description: '酸甜微辣，经典川菜', canteen: '第二食堂', stallName: '川味轩' },
    { id: 8, name: '牛肉拌面', price: 12, image: PLACEHOLDER_IMG, rating: 4.2, ratingCount: 76, tags: [], description: '清爽拌面，夏日首选', canteen: '第三食堂', stallName: '面面俱到' },
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

// ==================== 首页横幅（轮播图） ====================

/**
 * 获取首页轮播图数据
 * MVP mock —— 后期切换到 GET /api/home/banners
 */
export function getHomeBanners(): Promise<BannerItem[]> {
  const img = PLACEHOLDER_IMG
  return Promise.resolve([
    { title: '🍜 交大美食季', subtitle: '发现校园里的每一道美味', image: img },
    { title: '🔥 新菜品上架', subtitle: '一食堂二层新窗口开业', image: img },
    { title: '🏆 热门排行', subtitle: '同学们都在吃什么', image: img },
  ])
}

export function getCanteenList(): Promise<CanteenInfo[]> {
  return Promise.resolve([
    { name: '第一食堂', location: '一食堂一层', icon: '🍜' },
    { name: '第二食堂', location: '二食堂一层', icon: '🍛' },
    { name: '第三食堂', location: '三食堂一层', icon: '🥗' },
  ])
}

// ==================== 食堂背景图映射 ====================

/**
 * 获取食堂背景图映射 { 食堂名 → 图片路径 }
 * MVP mock —— 后期切换到 GET /api/canteen/images
 */
export function getCanteenImages(): Promise<Record<string, string>> {
  const img = PLACEHOLDER_IMG
  return Promise.resolve({
    '第一食堂': img,
    '第二食堂': img,
    '第三食堂': img,
  })
}

// ==================== 档口详情 ====================

/**
 * 获取档口详情
 * MVP mock —— 后期切换到 GET /api/stalls/{name}/detail
 */
export function getStallDetail(canteen: string, stallName: string): Promise<StallDetail> {
  const img = PLACEHOLDER_IMG
  return Promise.resolve({
    name: stallName,
    images: [img, img, img],
    location: canteen,
    description: `${canteen}·${stallName}，为您提供美味的校园餐饮体验。精选新鲜食材，用心烹制每一道菜品。`,
  })
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
