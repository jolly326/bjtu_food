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
  /** 是否微信绑定（管理端展示绑定关系，不泄露 openid） */
  wechatBound?: boolean;
  /**
   * 绑定校园邮箱（仅认证过才有；不公开给小程序）——**认证状态的唯一判据**：
   * 非空即已认证（2026-09-22 起 `verified` 字段已删，管理端按本字段派生，见 UserView）。
   */
  bindEmail?: string;
  created_at: Date;
  updated_at: Date;
}

// dish 菜品表
export interface Dish {
  id: bigint;
  stall_id: bigint;
  name: string;
  image?: string;
  /** 现价（元，API 层已由分转元；已含折扣）——价格展示的唯一数据源（§7.26） */
  price: number;
  description?: string;
  avg_rating: number;
  rating_count: number;
  status: string;
  /**
   * 档口名称（DishAdminVO 联表返回；列表/详情直读，不再经 store.stalls 反查）。
   * 菜品无独立审核，`audit_status` / `reject_reason` 从前端契约移除（对应后端列为历史列，不再读写）。
   */
  stallName?: string;
  /** 所属食堂名称（DishAdminVO 联表返回） */
  canteenName?: string;
  /**
   * 原价（元，可空，折扣前）。判据 `originalPrice > price` 时端上呈现删除线（§7.26）——
   * 展示值恒取 `price`，禁止双源切换（无独立促销价）。
   */
  originalPrice?: number;
  /**
   * ===== 描述四维（§7.28 描述维度替换，2026-09-20）=====
   * 替代原 `spiceLevel`（辣度）/ `region`（风味 / 菜系）两维；机器值 / 中文映射真源见
   * `constants/index.ts`（视图层禁止二次映射，统一经其文案函数输出）。
   */
  /** 荤素 / 饮食属性（单选）：meat=荤 / half=半荤 / veg=素 / halal=清真 */
  dietType?: string;
  /** 主料 / 食材（**机器值数组**，2026-09-23 §7.40 R4 由逗号分隔串改数组）：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice */
  ingredients?: string[];
  /** 口味（**机器值数组**，2026-09-23 §7.40 R4 改数组）：spicy/numbing/sour/sweet/salty/umami/light/heavy */
  flavorTags?: string[];
  /** 冷热（单选）：hot=热食 / room=常温 / ice=冰 */
  serveTemp?: string;
  /**
   * 菜品大类（单值枚举，2026-09-21 §7.34 / change `home-ui-refresh`）：
   * `set_meal` / `stir_fry` / `noodle` / `dry_pot` / `snack` / `soup_drink`。
   * **单值互斥**（一个菜品恰属一个大类），与上方描述四维（多值横切）**不是一类字段**、不得混用。
   * 中文标签 / 顺序 / 集合的唯一真源在后端字典 `GET /dishes/meal-types`，Web 端不得硬编码映射。
   * 分层：后台 `DishAdminVO` 出参 / `DishAdminReq` 入参含此字段；公开 `DishVO` 不含。
   */
  mealType?: string;
  created_at: Date;
  /** 真实消费：菜品编辑弹窗的「他人已修改」轻提示基线（Q-112 ①），删它会让并发覆盖提示失效 */
  updated_at: Date;
}

// review 评价表
// web 端评价列表 / 详情**不消费**服务端 enrich 的昵称 / 头像 / 菜名：
// 用户与菜品名一律按 user_id / dish_id 本地查 users / dishes 字典展示
// （ReviewManageView.getUserName / getDishName，WEB-03 降级显示；已注销用户兜底），
// 故本类型不声明这些字段（避免 stale 的「优先用它、不要退化本地查表」误导）。
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
}

// 评价 / 反馈无「内容安检状态」字段与复核动作——内容安全检测放行态与待复核态均放行、仅风险项拒绝，
// 后台不读写该字段（后端契约同源），前端不保留其类型与字段映射。

