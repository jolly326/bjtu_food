/**
 * 首页筛选 Tab 类型定义。
 *
 * 原位于 components/filter-tab.ts，但它是纯类型定义（无任何组件逻辑），
 * 且被 stores/dish.ts（store 层）引用，造成「store 依赖组件层」的分层倒挂。
 * 现迁移至 types/ 目录，供首页与 store 共同引用，消除分层倒挂（FilterBar 已移除）。
 */
export type FilterTabType = 'recommend' | 'tag' | 'category' | 'canteen'

export interface FilterTab {
  key: string
  label: string
  type: FilterTabType
  payload?: string
  /** category 类型时携带的品类 ID（category.id，查询 /dishes 用） */
  categoryId?: number
  /** canteen 类型时携带的食堂 ID（canteen.id，查询 /dishes 用 canteenId） */
  canteenId?: number
}
