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

/** 获取（必要时生成并持久化）本地游客 ID */
export function getGuestId(): string {
  let id = uni.getStorageSync(STORAGE_KEY_GUEST_ID) as string
  if (!id) {
    id = generateUuid()
    uni.setStorageSync(STORAGE_KEY_GUEST_ID, id)
  }
  return id
}

/** 展示用短 ID：取首 6 位大写（如 8F3A2C） */
export function getGuestShortId(): string {
  return getGuestId().replace(/-/g, '').slice(0, 6).toUpperCase()
}
