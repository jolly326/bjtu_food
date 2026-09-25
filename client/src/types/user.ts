/**
 * 用户信息 —— 恰 5 字段（spec §7.32 修订 / `auth-api-contract`）。
 *
 * 与登录 / 资料四条链路（`POST /auth/wechat-login`、`POST /auth/verify-email`、
 * `GET|PUT /auth/profile`）一一对应。契约不含以下字段：
 * - `verified`（`bindEmail` 非空的派生布尔，属同源冗余：认证判据统一为 `bindEmail != null`，
 *   端上经 `useUserStore().isVerified()` 单点派生；服务端同批删除 DB 列 `user.verified/verified_at`）
 * - `email`（微信体系下恒为 NULL，校园邮箱唯一来源 = `bindEmail`）
 * - `status`（端上零消费，禁用 / 注销由服务端 400 / 403 拦截）
 * - `guestShortId`（`id` 的纯派生值，改由展示层按 `id` 现算）
 *
 * user.role 列不纳入契约，类型不含 role 字段（全量用户即学生）。
 */
export interface UserInfo {
  id: number
  /** 学号/工号（校园身份，等于邮箱前缀）；游客态为 'wx_'+openid 尾 16 位 */
  username: string
  nickname: string
  avatar: string
  /** 已认证绑定邮箱（bind_email）；**认证状态的唯一判据**（非空即已认证），游客态为 undefined */
  bindEmail?: string
}
