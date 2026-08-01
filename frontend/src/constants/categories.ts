/**
 * 前端静态品类常量（task-02 / ARCH §2.3 & §4）
 * ------------------------------------------------------------
 * 架构师定稿：一期「分类宫格」品类 = 复用 Dish.tags 派生 + 前端静态常量，
 * 不新增 dish_category 表、不新增后台维护菜单（§3.x.3 克制原则）。
 *
 * categoryKey 必须与菜品 tags 取值一致（如 noodle/rice），
 * 点击品类 → GET /dishes?tag={categoryKey}（DishMapper.xml 的 tags LIKE 匹配）。
 *
 * 如需后台可维护品类，二期再立 dish_category 表 + /admin/categories（不在一期）。
 */

export interface CategoryItem {
  /** 与菜品 tags 一致的 key，用于 ?tag= 过滤 */
  key: string
  /** 展示名 */
  label: string
}

// 注：分类图标不再内联 emoji，统一由 find 页 categoryIcon(key) 映射到矢量 IconSvg
// （ic-noodle / ic-rice / ic-malatang 等，见 §0.5 图标表）。原 §0.6「MVP 统一 emoji」
// 红线已被「全面替换掉 emoji」决策推翻，且分类宫格实际已采用矢量图标渲染。
export const DISH_CATEGORIES: CategoryItem[] = [
  { key: 'noodle', label: '面食' },
  { key: 'rice', label: '盖饭' },
  { key: 'malatang', label: '麻辣烫' },
  { key: 'breakfast', label: '早餐' },
  { key: 'midnight', label: '夜宵' },
  { key: 'fastfood', label: '快餐' },
  { key: 'snack', label: '小吃' },
  { key: 'drink', label: '饮品' },
]

/** 辣度枚举（Dish.spiceLevel：0=不辣 1=微辣 2=中辣 3=重辣），task-03 */
export const SPICE_LEVELS: string[] = ['不辣', '微辣', '中辣', '重辣']

/** 分量枚举（Dish.portion：0=小 1=中 2=大），task-03 */
export const PORTION_LEVELS: string[] = ['小份', '中份', '大份']

/** 供应时段映射（Dish.servePeriod 逗号分隔 tag），task-03 */
export const SERVE_PERIOD_MAP: Record<string, string> = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  midnight: '夜宵',
}
