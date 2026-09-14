/**
 * 首页「开启定位可看距离」一次性提示（§7.16 第 3 条，2026-09-14 用户拍板）。
 *
 * 语义边界（与 spec 逐条对应）：
 * - **仅提示、不强制**：轻量确认（uni.showModal），文案说明「开启定位可看到菜品距离，
 *   拒绝也不影响浏览」；同意后走 `utils/location` 既有取坐标链路（不新写一套定位逻辑）；
 * - **只提示一次**：本地存储标记 `geo-prompted`，处理过即持久化，此后永不重复弹；
 * - **不阻塞首屏**：本模块只提供「弹提示」这一个异步动作，由调用方（首页）在首屏内容
 *   渲染完成后才触发，不在加载链路里 await；
 * - **拒绝即静默降级**：拒绝授权 / 取坐标失败一律返回 null，不弹二次提示、不报错，
 *   距离不显示、排序回退综合热度（既有降级行为不变）。
 */
import { getLocationIfAuthorized, getUserLocation, type UserLocation } from './location'
// uni.showModal 的 confirmColor 不接受 var()，须用真实色值；复用 tokens 登记的主色常量。
import { MODAL_CONFIRM_PRIMARY_COLOR } from '@/theme/tokens'

/** 本地存储标记键：已处理过定位引导（同意或拒绝均置位，保证不再打扰） */
const GEO_PROMPTED_KEY = 'geo-prompted'

/** 是否已处理过定位引导（标记存在即永不重复弹） */
export function hasGeoPrompted(): boolean {
  try {
    return !!uni.getStorageSync(GEO_PROMPTED_KEY)
  } catch {
    // 存储不可用（极少见）：按「已提示过」处理，宁可不提示也不反复打扰
    return true
  }
}

/** 落盘标记：用户已处理过定位引导（同意与拒绝同样落盘） */
export function markGeoPrompted(): void {
  try {
    uni.setStorageSync(GEO_PROMPTED_KEY, 1)
  } catch {
    /* 存储写入失败：静默，最多下次再提示一次，不阻断浏览 */
  }
}

/** 当前端是否具备「弹提示 + 取坐标」能力（仅微信小程序；H5/App 无定位，提示无意义） */
function canPromptGeo(): boolean {
  // @ts-ignore - 跨端兼容：非微信环境无 wx 全局对象
  if (typeof wx === 'undefined') return false
  return typeof uni.showModal === 'function'
}

/**
 * 弹一次性定位引导；返回**新获取到的坐标**供调用方写入 locationStore 并刷新距离。
 *
 * 返回值 null 的四种情形（调用方一律静默，不做任何额外提示）：
 * 1. 已提示过（标记存在 / 存储不可用）；
 * 2. 非微信小程序端（无定位能力）；
 * 3. 调用前已有坐标（已在显示距离，提示无意义，直接落标记）；
 * 4. 用户拒绝授权 / 取坐标失败（静默降级：不显示距离、按综合热度排序）。
 *
 * @param options.hasLocation 调用前是否已持有用户坐标（调用方从 locationStore 读取）
 * @returns 新获取的用户坐标；无需/无法获取时为 null
 */
export async function promptGeoOnce(options?: { hasLocation?: boolean }): Promise<UserLocation | null> {
  if (hasGeoPrompted()) return null
  if (!canPromptGeo()) return null

  // 已有坐标：距离本就在显示，无需引导；落标记保证此后不再打扰
  if (options?.hasLocation) {
    markGeoPrompted()
    return null
  }

  // 已授权但会话内尚未取到坐标（如冷启动瞬间）：直接补取坐标，不弹提示
  const authorized = await getLocationIfAuthorized()
  if (authorized) {
    markGeoPrompted()
    return authorized
  }

  // 先落标记再弹窗：即便用户在弹窗期间杀掉小程序，也不会再次打扰（「不再反复打扰」优先于「必然弹到」）
  markGeoPrompted()

  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '开启定位看距离',
      content: '开启定位可看到菜品距离你多远，方便就近选餐；不同意也不影响正常浏览。',
      confirmText: '开启定位',
      cancelText: '暂不开启',
      confirmColor: MODAL_CONFIRM_PRIMARY_COLOR,
      success: (res) => resolve(!!res.confirm),
      // 弹窗本身失败（极端场景）：静默，不再重试（标记已落盘）
      fail: () => resolve(false),
    })
  })
  if (!confirmed) return null

  // 同意 → 走既有取坐标链路；用户拒绝系统授权或取坐标失败均返回 null，静默降级
  return await getUserLocation()
}
