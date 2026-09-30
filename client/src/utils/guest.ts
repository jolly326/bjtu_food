/**
 * 本地游客身份：首次进入小程序时生成一个持久化的游客 ID（UUID 片段），
 * 作为"游客也有身份"的展示标识。登录后游客 ID 保留；退出登录回到游客身份时仍可用。
 * 仅前端本地展示用途，不关联后端数据。
 */
const STORAGE_KEY_GUEST_ID = 'guestId'

function generateUuid(): string {
  // 简化的 UUID v4（不依赖 crypto，uni-app 各端通用）
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** 获取（必要时生成并持久化）本地游客 ID（MP-06：零外部消费，收敛为模块私有） */
function getGuestId(): string {
  let id = uni.getStorageSync(STORAGE_KEY_GUEST_ID) as string
  if (!id) {
    id = generateUuid()
    uni.setStorageSync(STORAGE_KEY_GUEST_ID, id)
  }
  return id
}

/**
 * 本地游客标识：取本地游客 ID 首 6 位大写（如 8F3A2C）。
 * 仅作**兜底**——账号短标识由账号 `id` 派生「食客 + ID 尾 4 位」（spec §7.32）；
 * 无 `id`（静默登录未完成 / 失败）时才回退本值，保证展示不空白。
 */
export function getLocalGuestLabel(): string {
  return getGuestId().replace(/-/g, '').slice(0, 6).toUpperCase()
}

/**
 * 账号短标识（spec §7.32）：由账号 `id` 派生「食客 + ID 尾 4 位」（不足 4 位取全量）。
 *
 * `id` 不可得（静默登录未完成 / 失败）时回退 `fallback` —— 由调用方按展示语境决定：
 * · 「我的」页 → 传 `getLocalGuestLabel()`（本地游客 ID 兜底，保证不空白）；
 * · 「我的主页」→ 用默认「食客」。
 * **不伪造有效用户 ID**（回退值只保证展示非空）。
 *
 * 该派生原先在两页各写一份（且回退分支不一致），现统一到此处。
 */
export function deriveGuestLabel(id?: number | null, fallback = '食客'): string {
  if (!id) return fallback
  const s = String(id)
  return `食客${s.length > 4 ? s.slice(-4) : s}`
}
