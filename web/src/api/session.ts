/**
 * 管理端登录态存储（TD-23）。
 *
 * <p><b>口径真源</b>：[C1 管理员登录与访问控制](../../../docs/func/web/C-账号与访问/C1-管理员登录与访问控制.md)
 * · [api/web/auth.md](../../../docs/api/web/auth.md) —— 前端**只持有登录后签发的 JWT**，
 * 🔴 **不持有口令**（口令仅在登录表单提交瞬间存在于内存，不落任何存储、不进构建产物）。
 *
 * <p><b>为什么是 `sessionStorage` 而不是 `localStorage`</b>：关标签页即失效，
 * 把「浏览器长期留存」这块泄露面压到最小；管理端单人使用，重登成本可接受
 * （token 本身 24h 过期，无论如何都要重登）。
 *
 * <p><b>有效性不由本模块判定</b>：`getToken()` 非空只代表「有凭证」，凭证是否过期 / 被吊销
 * 须由 `GET /admin/auth/me` 实测（见 `router/index.ts` 的会话校验）。
 */

/** token 的存储键 */
const TOKEN_KEY = 'admin_token'
/** 登录名的存储键（仅用于端上回显，不参与鉴权） */
const USERNAME_KEY = 'admin_username'

/** 取管理端 JWT；无登录态返回 `null` */
export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

/** 是否持有凭证（**不等于**「已登录有效」） */
export function hasToken(): boolean {
  return getToken() !== null
}

/** 取回显用的登录名；未登录返回 `null` */
export function getUsername(): string | null {
  return sessionStorage.getItem(USERNAME_KEY)
}

/** 登录成功后落盘：token 供后续请求携带，登录名供身份区回显 */
export function setSession(token: string, username: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
  sessionStorage.setItem(USERNAME_KEY, username)
}

/**
 * 清登录态。
 *
 * <p>🔴 **token 与登录名必须同清**：只清 token 会留下「已登录」的假象，
 * 侧栏会继续显示上一任管理员的名字。
 */
export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USERNAME_KEY)
}
