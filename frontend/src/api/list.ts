import type { FoodList, ListCreate } from '@/types/list'

export function getMyLists(): Promise<FoodList[]> {
  return Promise.resolve([
    {
      id: 1, name: '交大必吃清单', description: '来了交大一定要吃的几道菜！',
      dishes: [
        { id: 1, name: '红烧牛肉面', price: 15, image: '', rating: 4.8, ratingCount: 256, tags: ['招牌菜', '必吃推荐'], description: '', canteen: '第一食堂', stallName: '面面俱到' },
        { id: 5, name: '酸菜鱼', price: 20, image: '', rating: 4.9, ratingCount: 423, tags: ['招牌菜', '必吃推荐'], description: '', canteen: '第二食堂', stallName: '渔味轩' },
        { id: 3, name: '麻辣香锅', price: 18, image: '', rating: 4.7, ratingCount: 312, tags: ['必吃推荐'], description: '', canteen: '第二食堂', stallName: '麻辣诱惑' },
      ],
      userId: 1, shareCount: 42, createTime: '2024-12-01',
    },
  ])
}

export function createList(data: ListCreate): Promise<FoodList> {
  return Promise.resolve({
    id: 2, name: data.name, description: data.description, dishes: [],
    userId: 1, shareCount: 0, createTime: new Date().toISOString().slice(0, 10),
  })
}


