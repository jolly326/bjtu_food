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
