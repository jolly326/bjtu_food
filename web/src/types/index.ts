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
  /** 楼层（如 1F/2F） */
  floor?: string;
  /** 窗口号 */
  windowNo?: string;
  /** 营业时间，如 10:00-20:00 */
  businessHours?: string;
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
  role: string;
  status: string;
  /** 是否已邮箱认证（0=游客未认证 / 1=已认证） */
  verified?: number;
  /** 微信 openid（管理端展示绑定关系，视图层脱敏展示尾号） */
  openid?: string;
  /** 绑定校园邮箱（仅认证过才有；管理端可展示，不公开给小程序） */
  bindEmail?: string;
  /** 游客短标识「食客+ID 尾 4 位」，昵称展示辅助 */
  guestShortId?: string;
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
  view_count: number;
  status: string;
  /** 辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣 */
  spiceLevel?: number;
  /** 分量枚举：0=小 1=中 2=大 */
  portion?: number;
  /** 供应时段 tag，逗号分隔：breakfast/lunch/dinner/midnight */
  servePeriod?: string;
  /** 是否限量（0=否 1=是） */
  limited?: number;
  /** 审核状态：pending / approved / rejected（与上下架 status 解耦） */
  audit_status?: string;
  /** 退回原因（audit_status=rejected 时由后台填写，回显学生端） */
  reject_reason?: string;
  /** 原价（元），用于折扣价展示；promoPrice 非空时为折扣价 */
  originalPrice?: number;
  /** 促销价（元，可空）；非空时视为有折扣 */
  promoPrice?: number;
  created_at: Date;
  updated_at: Date;
}

// UGC 审核记录（菜品 / 档口 / 食堂）
export interface AuditVO {
  id: bigint;
  type: 'dish' | 'stall' | 'canteen';
  name: string;
  price?: number;
  images?: string;
  description?: string;
  location?: string;
  submitterId?: bigint;
  submitterName?: string;
  audit_status: string;
  reject_reason?: string;
  created_at: Date;
  updated_at: Date;
}

// 后台管理员账号
export interface AdminUser {
  id: bigint;
  username: string;
  nickname?: string;
  role: string;
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

// banner 轮播/公告表
export interface Banner {
  id: bigint;
  title: string;
  image?: string;
  target_id?: bigint;
  target_type?: string;
  /** 跳转目标 URL（target_type=URL 时使用） */
  target_url?: string;
  canteen_id?: bigint;
  sort_order: number;
  status: string;
  created_at: Date;
  updated_at: Date;
}