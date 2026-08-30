export interface StallCardItem {
  id: number
  name: string
  image?: string
  description?: string
  /** 评分 */
  rating?: number
  /** 平均星级（后端 avgRating，与 rating 同源；组件优先展示 rating，缺省回落 avgRating） */
  avgRating?: number
  /** 菜品数 */
  dishCount?: number
  /** 人均（元，展示用，已为元） */
  perCapita?: number
  /** 档口位置（楼层/窗口等） */
  location?: string
  /** 展示用元信息（如「2F · 12道菜」），由父级拼接传入 */
  meta?: string
  /** 标签（如 招牌/清真…） */
  tags?: string[]
  /** 主要菜品（评分前3） */
  topDishes?: string[]
}
