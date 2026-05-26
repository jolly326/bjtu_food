export interface Dish {
  id: number
  name: string
  price: number
  image: string
  rating: number
  ratingCount: number
  favoriteCount?: number
  tags: string[]
  description: string
  canteen: string
  stallName: string
}

export interface RatingDistribution {
  star: number
  count: number
}

export interface DishDetail extends Dish {
  ratingDistribution: RatingDistribution[]
}

export interface DishQuery {
  keyword?: string
  canteen?: string
  minPrice?: number
  maxPrice?: number
  tags?: string[]
  sortBy?: 'rating' | 'favoriteCount' | 'price'
}
