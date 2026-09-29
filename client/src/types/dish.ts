/**
 * 菜品类型（列表 / 详情出参拆分，见 docs/feature/client-首页菜品浏览.md D 项）
 *
 * **列表行 `DishListItem`**（`GET /dishes`，`DishListItemVO` **恰为 8 字段**）：
 *   id / name / coverImage / price / originalPrice / avgRating / canteenName / stallName
 * **详情 `DishDetail`**（`GET /dishes/{id}`，`DishDetailVO` **恰为 11 字段**）：
 *   id / name / price / originalPrice / description / images / stallName / canteenName /
 *   floor / avgRating / attributes
 *
 * 端上定型说明：
 * - 金额一律「元」：API 层由分转元（`fenToYuan`），视图层直取展示；
 * - `canteenName` → 端上别名 `canteen`，`avgRating` → `rating`；
 * - 列表**不含**详情专属字段（`description` / `images` / `floor` / `attributes`）；
 *   列表图片只给 `coverImage`（后端首图绝对 URL；无图空串）——列表**不得回流** `images` 数组；
 * - 描述属性 `attributes` 由**后端**把机器值与中文**一并整理下发**（`value` + `label`）：
 *   端上**直接渲染 `label`、零翻译、不拉字典**，SHALL NOT 维护「机器值 → 中文」映射表（R4）。
 *
 * DishDetailVO 不含以下字段（契约之外不出现）：promoPrice / status / createdAt / canteenId /
 * viewCount / tags / ratingCount / ratingDistribution / dietType / ingredients / flavorTags /
 * serveTemp / spiceLevel / region / windowNo / updatedAt / latitude / longitude / distance /
 * stallId。
 */
export interface DishListItem {
  id: number
  name: string
  /** 现价（元；API 层已由分转元）。**价格展示唯一数据源** */
  price: number
  /** 原价（元，可空）；有值且大于 price 时端上在原价加删除线表示折扣 */
  originalPrice?: number | null
  /** 封面首图（后端 `coverImage`，列表唯一图片字段；无图空串） */
  coverImage: string
  /** 平均评分（后端 avgRating，口径 = 仅未隐藏评价）；**零评价为 `null`（不渲染评分区）** */
  rating: number | null
  /** 食堂名称（后端 canteenName） */
  canteen: string
  /** 档口名称 */
  stallName: string
}

/**
 * 详情描述属性项（后端已整理，端上直渲 `label`）。
 *
 * `value` 与 `label` **同构**：`single` 维度为字符串、`multi` 维度为字符串数组。
 * 仅含该菜品实际拥有的维度、按后端维度展示顺序排列（自描述、有序）。
 */
export interface DishAttributeItem {
  /** 维度键（camelCase；编辑态提交时即 `attributes` 的键） */
  fieldKey: string
  /** 维度中文名（如「饮食属性」） */
  name: string
  /** 机器值：single → 字符串；multi → 字符串数组 */
  value: string | string[]
  /** 中文标签，与 `value` 同构（后端整理，端上零翻译） */
  label: string | string[]
}

/** 详情（`GET /dishes/{id}`）：列表 8 字段之外，额外含详情专属字段与描述属性 */
export interface DishDetail {
  id: number
  name: string
  price: number
  originalPrice?: number | null
  description: string
  /** 多图 URL 数组（详情专属；恒为数组，无图为空数组） */
  images: string[]
  /** 平均评分；**该菜品零评价时为 `null`** —— 端上按判空呈现「暂无评分」 */
  rating: number | null
  canteen: string
  stallName: string
  /** 档口所属楼层（如 1F/2F；详情专属） */
  floor?: string
  /** 描述属性（机器值 + 中文，后端整理下发；端上直渲 `label`） */
  attributes: DishAttributeItem[]
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
  /** 平均评分；零评价为 `null`（不渲染评分区） */
  rating?: number | null
  /** 原价（元，> price 时划线展示表示折扣，判据 `utils/dish.hasDiscount`） */
  originalPrice?: number | null
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
 * 猜你喜欢（GET /dishes/for-you）：语义为「**随机抽取在售菜品名**」
 * （不看热度、不排序、不做个性化，故不缓存）。
 *
 * 出参**只有 `name`**（随机语义下无其他消费点）；**契约留扩展位**：将来升级为个性化 /
 * 推荐算法时端上契约不变（仍为 `name` 列表），只换服务端取数逻辑。
 */
export interface GuessLike {
  /** 菜品名（随机抽取的在售菜品）；端上唯一消费字段（chip 文案 + 点击起搜） */
  name: string
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
