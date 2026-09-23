/**
 * 菜品类型（2026-09-22 列表 / 详情出参拆分，见 docs/feature/client-首页菜品浏览.md D 项）
 *
 * **列表行 `DishListItem`**（`GET /dishes`，`DishListItemVO` **恰为 8 字段**）：
 *   id / name / coverImage / price / originalPrice / avgRating / canteenName / stallName
 * **详情 `DishDetail`**（`GET /dishes/{id}`，`DishDetailVO` 15 字段 + `ratingDistribution`）：
 *   id / name / price / originalPrice / description / images / stallName / canteenName /
 *   floor / avgRating / ratingCount / dietType / ingredients / flavorTags / serveTemp
 *
 * 端上定型说明：
 * - 金额一律「元」：API 层由分转元（`fenToYuan`），视图层直取展示；
 * - `canteenName` → 端上别名 `canteen`，`avgRating` → `rating`；
 * - 列表**不含**详情专属字段（`description` / `images` / `floor` / `ratingCount` / 四维）；
 *   列表图片只给 `coverImage`（后端首图绝对 URL；无图空串）——列表**不得回流** `images` 数组；
 * - 描述四维**下发机器值**（`dietType` / `serveTemp` 单值，`ingredients` / `flavorTags` 为数组）；
 *   中文展示值由**四维字典端点**提供（见 `api/dish-attribute.ts` + `stores/dish-attribute.ts`），
 *   端上 SHALL NOT 硬编码「机器值 → 中文」映射表（2026-09-23 §7.40 R4）。
 *
 * 已全链删除（SHALL NOT 回流）：promoPrice / status / createdAt / canteenId / stallId /
 * viewCount / tags / spiceLevel / region / windowNo / updatedAt / latitude / longitude /
 * distance（坐标与距离随「计算距离」概念整体下线）。
 */
export interface DishListItem {
  id: number
  name: string
  /** 现价（元；API 层已由分转元）。**价格展示唯一数据源** */
  price: number
  /** 原价（元，可空）；有值且大于 price 时端上在原价加删除线表示折扣 */
  originalPrice?: number
  /** 封面首图（后端 `coverImage`，列表唯一图片字段；无图空串） */
  coverImage: string
  /** 平均评分（后端 avgRating，口径 = 仅未隐藏评价） */
  rating: number
  /** 食堂名称（后端 canteenName） */
  canteen: string
  /** 档口名称 */
  stallName: string
}

interface RatingDistribution {
  star: number
  count: number
}

/** 详情（`GET /dishes/{id}`）：列表 8 字段之外，额外含详情专属字段与评分分布 */
export interface DishDetail {
  id: number
  name: string
  price: number
  originalPrice?: number
  description: string
  /** 多图 URL 数组（详情专属） */
  images: string[]
  /** 首图派生字段（= images[0]，详情图集展示便利；非后端出参） */
  image: string
  rating: number
  ratingCount: number
  canteen: string
  stallName: string
  /** 档口所属楼层（如 1F/2F；详情专属） */
  floor?: string
  /** 描述四维·荤素（**机器值**：meat / half / veg / halal；中文由字典提供） */
  dietType?: string
  /** 描述四维·主料（**机器值数组**：如 `['chicken','rice']`；中文由字典提供） */
  ingredients?: string[]
  /** 描述四维·口味（**机器值数组**：如 `['spicy','sour']`；吸收原「辣度」语义，中文由字典提供） */
  flavorTags?: string[]
  /** 描述四维·冷热（**机器值**：hot / room / ice；中文由字典提供） */
  serveTemp?: string
  ratingDistribution: RatingDistribution[]
}

/**
 * 列表查询参数（`GET /dishes`，**完整参数集恰为 4 项**：page / pageSize / keyword / mealType）。
 * <p>
 * 2026-09-22：`canteenId` / `minPrice` / `maxPrice` 随「食堂 / 价格筛选全量下线」删除
 * （SHALL NOT 回流）；`sortBy` / `sortOrder` 已于 2026-09-21 收敛（排序恒为服务端热度倒序）。
 */
export interface DishQuery {
  keyword?: string
  /** 菜品大类筛选（首页横向标签栏；值为大类枚举键；不传 = 全部） */
  mealType?: string
  page?: number
  pageSize?: number
}

/**
 * 猜你喜欢词（GET /dishes/for-you）。
 * 2026-09-22 change search-page-refresh：由原「热搜词」类型改名——语义由
 * 「热度派生热搜词」变为「**随机抽取在售菜品名**」（不看热度、不排序、不做个性化，故不缓存）；
 * **契约留扩展位**：将来升级为个性化 / 推荐算法时仍为 `keyword` 列表，端上无需改造。
 */
export interface GuessLike {
  keyword: string
}

/**
 * 菜品大类字典项（`GET /dishes/meal-types`）：
 * 文案 / 顺序 / 子集全由后端下发（空类自动隐藏），**端上不得维护任何中文映射**。
 */
export interface MealType {
  key: string
  label: string
  order: number
}
