export type FilterTabType = 'recommend' | 'tag' | 'category'

export interface FilterTab {
  key: string
  label: string
  type: FilterTabType
  payload?: string
  /** category 类型时携带的品类 ID（category.id，查询 /dishes 用） */
  categoryId?: number
}
