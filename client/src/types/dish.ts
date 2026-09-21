/**
 * 菜品类型（公开 DishVO 契约，15 字段 · 2026-09-20 dish-detail-remediation 收敛）
 *
 * 公开菜品契约字段集恰为 15 个（`specs/dish-field-contract`）：
 * id / name / price / originalPrice / description / images /
 * stallName / canteenName / floor / avgRating / ratingCount /
 * dietType / ingredients / flavorTags / serveTemp。
 *
 * 端上定型说明：
 * - `canteenName` → 端上别名 `canteen`（历史消费点沿用），`avgRating` → `rating`；
 * - `image`（首图封面）由 `images[0]` 派生，属端上展示便利字段、非后端出参；
 * - `dietType` / `serveTemp` 等四维在 API 层已由机器值映射为中文展示值（见 api/dish.ts），
 *   视图层直取渲染，禁止二次映射。
 *
 * 已全链删除（SHALL NOT 回流）：promoPrice / status / createdAt / canteenId / stallId /
 * viewCount / tags / spiceLevel / region / windowNo / updatedAt / latitude / longitude /
 * distance（坐标与距离随「计算距离」概念整体下线）。
 */
export interface Dish {
  id: number
  name: string
  /** 现价（元；API 层已由分转元）。**价格展示唯一数据源** */
  price: number
  /** 原价（元，可空）；有值且大于 price 时端上在原价加删除线表示折扣 */
  originalPrice?: number
  /** 封面首图（= images[0]，端上派生字段） */
  image: string
  images?: string[]
  /** 平均评分（后端 avgRating，口径 = 仅未隐藏评价） */
  rating: number
  /** 评价数（同上口径） */
  ratingCount: number
  description: string
  /** 食堂名称（后端 canteenName） */
  canteen: string
  /** 档口名称 */
  stallName: string
  /** 档口所属楼层（如 1F/2F） */
  floor?: string
  /** 描述四维·荤素（中文展示值：荤 / 半荤 / 素 / 清真） */
  dietType?: string
  /** 描述四维·主料（中文展示值，顿号分隔） */
  ingredients?: string
  /** 描述四维·口味（中文展示值，顿号分隔；吸收原「辣度」语义） */
  flavorTags?: string
  /** 描述四维·冷热（中文展示值：热食 / 常温 / 冰） */
  serveTemp?: string
}

interface RatingDistribution {
  star: number
  count: number
}

export interface DishDetail extends Dish {
  ratingDistribution: RatingDistribution[]
}

export type DishSortBy = 'heat' | 'rating' | 'price' | 'created_at'

export interface DishQuery {
  keyword?: string
  /** 食堂 ID（多维筛选） */
  canteenId?: number
  /** 价格区间（前端「元」，API 层转分提交） */
  minPrice?: number
  maxPrice?: number
  /** 排序维度（ARCH §3.1：heat/rating/price/created_at；端上默认不传） */
  sortBy?: DishSortBy
  sortOrder?: 'asc' | 'desc'
  page?: number
  pageSize?: number
}

/** 热搜词（GET /dishes/hot-search；一期为菜品热度派生的热门词条） */
export interface HotSearch {
  keyword: string
}
