export interface Review {
  id: number
  userId: number
  userName: string
  userAvatar: string
  dishId: number
  rating: number
  content: string
  images: string[]
  createTime: string
}

export interface ReviewSubmit {
  dishId: number
  rating: number
  content: string
  images: string[]
}
