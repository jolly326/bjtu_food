/**
 * 用户信息 —— 恰 4 字段（口径见 docs/func/client/C-账号与身份/C1-微信静默登录与游客态.md）。
 *
 * 与登录 / 资料四条链路（`POST /auth/wechat-login`、`POST /auth/verify-email`、
 * `GET|PUT /auth/profile`）一一对应。契约不含以下字段：
 * - `verified`（`bindEmail` 非空的派生布尔，属同源冗余：认证判据统一为 `bindEmail != null`，
 *   端上经 `useUserStore().isVerified()` 单点派生）
 * - `createdAt`（注册时间端上零消费，不出参）
 * - `email`（微信体系下恒为 NULL，校园邮箱唯一来源 = `bindEmail`）
 * - `status`（端上零消费，禁用 / 注销由服务端 400 / 403 拦截）
 * - `guestShortId`（`id` 的纯派生值，改由展示层按 `id` 现算）
 * - `username`（**按「零消费即删」移出出参**：两处身份副行统一渲染 `bindEmail`，
 *   不再渲染裸学号；账号标识保留在 user 表与 JWT 载荷，属服务端内部字段）
 *
 * user.role 列不纳入契约，类型不含 role 字段（全量用户即学生）。
 */
export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  /** 已认证绑定邮箱（bind_email）；**认证状态的唯一判据**（非空即已认证） */
  bindEmail?: string
}
