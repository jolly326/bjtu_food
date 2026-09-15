// canteen 食堂表
// 2026-09-14（Q-113 / PR-14）契约收紧：食堂是**菜品筛选属性字典**，非业务实体，
// 生命周期只有「新增 / 改名」——后端已移除 status / auditStatus / rejectReason（列即将 DROP），
// 故此处不再声明任何实体状态字段（营业/停业/审核态一律不设）。
export interface Canteen {
  id: bigint;
  name: string;
  image?: string;
  location?: string;
  description?: string;
  sort_order: number;
  created_at: Date;
  updated_at: Date;
}

// stall 档口表
// 2026-09-14（Q-113 / PR-14）契约收紧：档口同为**菜品筛选属性字典**，非业务实体，
// 后端已移除 status / auditStatus / rejectReason（列即将 DROP）。
// floor（楼层）/ windowNo（窗口号）保留——端上有消费。
export interface Stall {
  id: bigint;
  canteen_id: bigint;
  name: string;
  image?: string;
  location?: string;
  description?: string;
  avg_rating: number;
  sort_order: number;
  /** 楼层（如 1F/2F）——保留 */
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
  /** 风味/菜系（东北 / 川湘 / 粤式 / 西北 / 清真 / 其他；空=未填） */
  region?: string;
  /**
   * 档口名称（DishAdminVO 联表返回；列表/详情直读，不再经 store.stalls 反查）。
   * 注（§7.23 第 4 条，2026-09-15）：菜品无独立审核，原 audit_status / reject_reason
   * 已随「菜品审核 UI 下线」从前端契约移除（后端列为退役历史列，不再读写）。
   */
  stallName?: string;
  /** 所属食堂名称（DishAdminVO 联表返回） */
  canteenName?: string;
  /** 原价（元），用于折扣价展示；promoPrice 非空时为折扣价 */
  originalPrice?: number;
  /** 促销价（元，可空）；非空时视为有折扣 */
  promoPrice?: number;
  created_at: Date;
  updated_at: Date;
}

// 注：AuditVO（UGC 审核记录：菜品 / 档口 / 食堂）已于 2026-09-14 随审核中心死代码删除（Q-107 / P1-01）：
// /admin/audit/** 三条接口前端零消费（管理员录入即 approved，pending 无新来源），无任何页面消费该类型。

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
  created_at: Date;
  updated_at: Date;
}

// 注（2026-09-15 拍板：取消人工复核）：评价 / 反馈的「内容安检状态」字段及其「安检态 / 复核动作」
// 两个联合类型已随人工复核职责取消一并删除——内容机检放行态与待复核态均放行、仅风险项拒绝，
// 后台不再读取或写入该字段（后端契约同源移除），前端不再保留其类型与字段映射。

