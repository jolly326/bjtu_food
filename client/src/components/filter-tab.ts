export type FilterTabType = 'recommend' | 'tag'

export interface FilterTab {
  key: string
  label: string
  type: FilterTabType
  payload?: string
}
