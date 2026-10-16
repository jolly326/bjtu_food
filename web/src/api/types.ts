/** 运营端接口出参与入参类型（字段口径见 docs/api/web/）。 */

export type Page<T> = { records: T[]; total: number }

export type CanteenOut = { id: number; name: string; stallCount: number; updatedAt: string }

export type StallBody = {
  canteenId: number
  name: string
  posterUrl?: string | null
  floor?: string | null
  windowNo?: string | null
}
export type StallOut = StallBody & {
  id: number
  canteenName: string
  dishCount: number
  updatedAt: string
}

export type DishBody = {
  stallId: number
  name: string
  price: number
  imageUrl?: string | null
  status: string
}
export type DishOut = DishBody & {
  id: number
  stallName: string
  canteenName: string
  avgReaction: number | null
  reactionCount: number
  updatedAt: string
}

export type RecordOut = {
  id: number
  dishId: number
  dishName: string
  canteenName: string
  stallName: string
  reaction: number | null
  wantedAt: string
  reactedAt: string | null
}

export type IssueOut = {
  id: number
  dishId: number
  dishName: string
  canteenName: string
  stallName: string
  optionId: number
  optionName: string
  createdAt: string
  handledAt: string | null
}

export type OptionOut = { id: number; name: string; refCount: number; updatedAt: string }
