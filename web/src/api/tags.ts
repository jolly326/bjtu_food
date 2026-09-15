/**
 * 菜品标签权威值域（F-003）：仅 recommended / signature 两值。
 * 权威依据：docs/database.md:104、server schema.sql:115、client TAG_MAP（api/dish.ts:10-13）、
 * DishMapper.xml FIND_IN_SET —— Web 写库值必须与其一致，禁止写入中文或其他值，
 * 否则小程序端 FIND_IN_SET 筛选将失效。
 */

/** 招牌菜（写库值） */
export const SIGNATURE_TAG = 'signature'
/** 必吃推荐（写库值）：仅本文件自用（外部消费出口为 TAG_OPTIONS / tagDisplay），故不导出 */
const RECOMMENDED_TAG = 'recommended'

/** 标签选项：value = 写库英文值，label = 中文展示文案（对齐 client TAG_MAP） */
export const TAG_OPTIONS = [
  { value: RECOMMENDED_TAG, label: '必吃推荐' },
  { value: SIGNATURE_TAG, label: '招牌菜' },
] as const

/** 写库英文值 → 中文展示文案；未知值（存量脏数据）原样透出，不吞不译 */
export function tagDisplay(tag: string): string {
  return TAG_OPTIONS.find(o => o.value === tag)?.label ?? tag
}
