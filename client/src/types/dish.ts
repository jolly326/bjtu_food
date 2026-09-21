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
 * - 封面首图**直接取 `images[0]`**（2026-09-21 §7.33：原端上派生字段 `image` 与 `images[0]` 同值重复，已删除）；
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
  /** 菜品图片数组（首图作封面：消费方取 `images[0]`） */
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

export interface DishQuery {
  keyword?: string
  /** 食堂 ID（多维筛选） */
  canteenId?: number
  /**
   * 菜品大类枚举键（2026-09-21 §7.34；单值、互斥）。
   * 合法值域由后端白名单定义（非法值后端 400，端上不降级）；
   * 端上**不维护**枚举键 → 中文标签的映射，标签文案一律来自 `getMealTypes()` 响应。
   */
  mealType?: string
  /** 价格区间（前端「元」，API 层转分提交） */
  minPrice?: number
  maxPrice?: number
  page?: number
  pageSize?: number
}

/**
 * 菜品大类字典项（`GET /dishes/meal-types` 出参，2026-09-21 §7.34）。
 *
 * 端上标签栏渲染源：第一项「全部」由端上固定渲染（对应**不传** `mealType`），
 * 其余项**完全**按本结构渲染（`label` 文案 + `order` 顺序）——
 * 端上**禁止**硬编码任何大类中文名或标签清单（见 `dish-meal-category` spec）。
 */
export interface MealType {
  /** 大类枚举键（筛选用，透传 `mealType` 查询参数） */
  key: string
  /** 中文标签（展示文案，唯一真源在后端常量） */
  label: string
  /** 展示顺序（后端已按 order 升序下发，端上不再重排） */
  order: number
}

/** 热搜词（GET /dishes/hot-search；一期为菜品热度派生的热门词条） */
export interface HotSearch {
  keyword: string
}
