/**
 * 首页筛选 Tab 类型定义。
 *
 * 原位于 components/filter-tab.ts，但它是纯类型定义（无任何组件逻辑），
 * 且被 stores/dish.ts（store 层）引用，造成「store 依赖组件层」的分层倒挂。
 * 现迁移至 types/ 目录，供组件（FilterBar/首页）与 store 共同引用，消除分层倒挂。
 */
export type FilterTabType = 'recommend' | 'tag' | 'category'

export interface FilterTab {
  key: string
  label: string
  type: FilterTabType
  payload?: string
  /** category 类型时携带的品类 ID（category.id，查询 /dishes 用） */
  categoryId?: number
}
