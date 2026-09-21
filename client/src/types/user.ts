/**
 * 用户信息 —— 恰 6 字段（2026-09-21 spec §7.32 / `auth-api-contract`）。
 *
 * 与登录 / 资料四条链路（`POST /auth/wechat-login`、`POST /auth/verify-email`、
 * `GET|PUT /auth/profile`）一一对应。已删除且不得回流：
 * - `email`（微信体系下恒为 NULL，校园邮箱唯一来源 = `bindEmail`）
 * - `status`（端上零消费，禁用 / 注销由服务端 400 / 403 拦截）
 * - `guestShortId`（`id` 的纯派生值，改由展示层按 `id` 现算）
 *
 * user.role 列已退役（2026-09-15：全量用户即学生），类型不含 role 字段。
 */
export interface UserInfo {
  id: number
  /** 学号/工号（校园身份，等于邮箱前缀）；游客态为 'wx_'+openid 尾 16 位 */
  username: string
  nickname: string
  avatar: string
  /** 是否已邮箱认证（微信登录体系 §5.y）：true 解锁 UGC 写操作 */
  verified: boolean
  /** 已认证绑定邮箱（bind_email）；校园邮箱唯一来源，未认证为 undefined */
  bindEmail?: string
}
