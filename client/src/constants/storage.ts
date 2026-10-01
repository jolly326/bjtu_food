/**
 * 本地存储键（唯一真源）。
 *
 * 消费方：`stores/user.ts`（登录态读写）、`api/http.ts`（401 兜底清理 / 取 token）。
 * 禁止在调用点再写 `'token'` / `'userInfo'` 裸字面量 —— 键名变更只改这里。
 */
export const STORAGE_KEY_TOKEN = 'token'
export const STORAGE_KEY_USER = 'userInfo'
