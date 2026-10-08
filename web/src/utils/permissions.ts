import { getRole } from '@/api/session'

/**
 * 管理端角色与入口隐显判据（口径真源：[secur/web/登录与凭证.md](../../../docs/secur/web/登录与凭证.md) §2.5）。
 *
 * <p>🔴 <b>本模块只负责「体验」，不负责「安全」</b>：前端隐显入口是为了让低权限角色
 * 不去点注定 `403` 的按钮；真正的门控在服务端（`AdminAuthFilter` 按 HTTP 方法 + 角色判定），
 * 改动本模块的返回值不会让任何越权请求通过。
 *
 * <p><b>fail-closed</b>：角色缺失 / 未知一律按 `viewer`（只读）处理 ——
 * 与服务端 `AdminAuthFilter#normalizeRole` 同一口径，避免「角色没取到 ⇒ 误显示删除按钮」。
 */
export type AdminRole = 'super' | 'operator' | 'viewer'

/** 角色中文名（身份区展示用） */
const ROLE_LABELS: Record<AdminRole, string> = {
  super: '超级管理员',
  operator: '运营',
  viewer: '只读',
}

/** 归一到三档角色；未知 / 空一律 `viewer` */
export function currentRole(): AdminRole {
  const raw = getRole()
  return raw === 'super' || raw === 'operator' ? raw : 'viewer'
}

/** 角色展示名 */
export function roleLabel(): string {
  return ROLE_LABELS[currentRole()]
}

/** 能否执行写操作（新增 / 修改 / 处置 / 启停）：`viewer` 不可 */
export function canWrite(): boolean {
  return currentRole() !== 'viewer'
}

/** 能否执行删除（不可逆操作）：**仅 `super`** —— 与服务端「DELETE 仅 super」对齐 */
export function canDelete(): boolean {
  return currentRole() === 'super'
}
