// canteen 食堂表
export interface Canteen {
  id: bigint;
  name: string;
  image?: string;
  location?: string;
  description?: string;
  sort_order: number;
  status: string;
  created_at: Date;
  updated_at: Date;
}

// stall 档口表
export interface Stall {
  id: bigint;
  canteen_id: bigint;
  name: string;
  image?: string;
  location?: string;
  description?: string;
  avg_rating: number;
  sort_order: number;
  status: string;
  created_at: Date;
  updated_at: Date;
}

// user 用户表
export interface User {
  id: bigint;
  username: string;
  password: string;
  nickname?: string;
  avatar?: string;
  stall_id?: bigint;
  role: string;
  status: string;
  created_at: Date;
  updated_at: Date;
}

// dish 菜品表
export interface Dish {
  id: bigint;
  stall_id: bigint;
  name: string;
  image?: string;
  price: number;
  tags?: string;
  description?: string;
  avg_rating: number;
  rating_count: number;
  favorite_count: number;
  view_count: number;
  status: string;
  created_at: Date;
  updated_at: Date;
}

// review 评价表
export interface Review {
  id: bigint;
  user_id: bigint;
  dish_id: bigint;
  rating: number;
  content?: string;
  images?: string;
  is_hidden: number;
  created_at: Date;
  updated_at: Date;
}

// favorite 收藏表
export interface Favorite {
  id: bigint;
  user_id: bigint;
  dish_id: bigint;
  created_at: Date;
}

// banner 轮播/公告表
export interface Banner {
  id: bigint;
  title: string;
  image?: string;
  type: string;
  target_id?: bigint;
  target_type?: string;
  canteen_id?: bigint;
  sort_order: number;
  status: string;
  created_at: Date;
  updated_at: Date;
}