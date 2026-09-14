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
  /** 是否微信绑定（管理端展示绑定关系，不泄露 openid） */
  wechatBound?: boolean;
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
  /** 搜索别名（逗号分隔，管理员配置；搜索命中 name 或 alias） */
  alias?: string;
  avg_rating: number;
  rating_count: number;
  view_count: number;
  status: string;
  /** 辣度枚举：0=不辣 1=微辣 2=中辣 3=重辣 */
  spiceLevel?: number;
  /** 分量枚举：0=小 1=中 2=大 */
  portion?: number;
  /** 风味/菜系（东北 / 川湘 / 粤式 / 西北 / 清真 / 其他；空=未填） */
  region?: string;
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

// review 评价表
export interface Review {
  id: bigint;
  user_id: bigint;
  dish_id: bigint;
  rating: number;
  content?: string;
  /** 配图列表（adapter 归一为 string[]；COS 公网地址可直接 <img> 展示） */
  images?: string[];
  is_hidden: number;
  /** 内容安检状态：pass=正常 / review=待复核 / rejected=已驳回 */
  secState?: SecState;
  created_at: Date;
  updated_at: Date;
}

/** 内容安检状态（评价 / 反馈共用）：pass=正常 / review=待复核 / rejected=已驳回 */
export type SecState = 'pass' | 'review' | 'rejected';

/** 安检复核动作：放行 pass / 驳回 rejected（review 为待复核态，不可直接写入） */
export type SecAction = 'pass' | 'rejected';

