/**
 * 菜品类型（列表 / 详情出参拆分，见 docs/feature/client-首页菜品浏览.md D 项）
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
 *   端上 SHALL NOT 硬编码「机器值 → 中文」映射表（§7.40 R4）。
 *
 * DishDetailVO 不含以下字段（契约之外不出现）：promoPrice / status / createdAt / canteenId /
 * viewCount / tags / spiceLevel / region / windowNo / updatedAt / latitude / longitude /
 * distance / stallId。
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

export interface RatingDistribution {
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
 * 搜索结果项（**页面视图模型**，非接口出参）
 *
 * 搜索页把 `GET /dishes` 的行投影成它，`DishResultCard` 组件按它渲染（由 find/index 编排）。
 * ⚠️ 与 `DishListItem` 的区别：字段按**展示语义**收敛（`coverImage → image`），且只含结果卡用到的字段。
 * 单一来源：原先 find 页 `MixedResult` 与 `FindResults` 内 `MixedResultItem` 是逐字段重复的两份定义
 * （UI 统一 Loop Round 17 合并）。
 */
export interface MixedResultItem {
  type: 'dish'
  id?: number
  name: string
  /** 列表唯一图片字段（后端 coverImage；无图空串 → 结果卡占位空态） */
  image?: string
  /** 副信息：菜品 →「食堂 · 档口」（`utils/dish.joinLocation`） */
  sub?: string
  /** 价格（元，api 层已转） */
  price?: number
  /** 平均评分 */
  rating?: number
  /** 原价（元，> price 时划线展示表示折扣，判据 `utils/dish.hasDiscount`） */
  originalPrice?: number
}

/**
 * 列表查询参数（`GET /dishes`，**完整参数集恰为 5 项**：page / pageSize / keyword / mealType / seed）。
 * <p>
 * `canteenId` / `minPrice` / `maxPrice` 不纳入查询参数（食堂 / 价格筛选不提供）；
 * `sortBy` / `sortOrder` 不传（排序由服务端唯一决定：推荐流按 `seed` 伪随机序、其余热度倒序）。
 */
export interface DishQuery {
  keyword?: string
  /** 菜品大类筛选（首页横向标签栏；值为大类枚举键；不传 = 全部） */
  mealType?: string
  /**
   * 推荐流会话随机种子（2026-09-27 方案 C）：仅首页「为你推荐」流（不传 keyword/mealType）下发。
   * 服务端按 `CRC32(CONCAT(seed,'-',id)), id` 做稳定伪随机排序——同 seed 全序恒定（翻页不重不漏），
   * 端上在每次列表 reset 时重掷（首屏 / 切回「为你推荐」/ 失败重试），翻页沿用同一值。
   * 大类 / 搜索流不传（维持热度倒序）。
   */
  seed?: string
  page?: number
  pageSize?: number
}

/**
 * 猜你喜欢词（GET /dishes/for-you）。
 * 猜你喜欢词（GET /dishes/for-you）：语义为「**随机抽取在售菜品名**」（不看热度、不排序、不做个性化，故不缓存）；
 * **契约留扩展位**：将来升级为个性化 / 推荐算法时仍为 `keyword` 列表，端上无需改造。
 */
export interface GuessLike {
  keyword: string
}

/**
 * 菜品大类字典项（`GET /dishes/meal-types`）：
 * 文案 / 顺序 / 子集全由后端下发（空类自动隐藏），**端上不得维护任何中文映射**。
 *
 * ⚠️ 方案 B 契约：首项由后端下发 `{ value: null, label: "为你推荐" }`，
 * 故 `value` 允许为 `string | null`（`null` 表示不传 mealType，拉取推荐流）。
 * `order` 是服务端排序用的内部字段，端上按返回顺序渲染 → 按「零消费即删」不进入本类型。
 */
export interface MealType {
  value: string | null
  label: string
}
