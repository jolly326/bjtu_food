/**
 * 轻量定位工具：首页瀑布流按距离排序/展示「距你 Xm」所需。
 *
 * 设计要点：
 * - 仅在微信小程序端真正调用 uni.getLocation（manifest 已声明 scope.userLocation 权限）；
 * - H5 / App 等其他端无定位能力时直接 resolve(null, null)，不弹窗、不报错，降级为「无距离」展示；
 * - 用户拒绝授权或获取失败时同样 resolve(null, null)，保证首页正常加载（不阻断主流程）。
 */

export interface UserLocation {
  lat: number
  lng: number
}

/**
 * 校区中心兜底坐标（GCJ-02，北京交通大学海淀校区近似）。
 * 当用户未授权 / 无法获取定位（如 H5 预览）时，用此坐标计算「距你」距离，
 * 保证菜品卡距离字段始终有值；用户授权后自动切换为真实坐标。
 */
export const CAMPUS_CENTER: UserLocation = { lat: 39.9538, lng: 116.3354 }

/** 解析用户坐标；任意异常（无 API / 未授权 / 超时）均返回 null，由调用方决定降级行为 */
export function getUserLocation(): Promise<UserLocation | null> {
  return new Promise((resolve) => {
    // @ts-ignore - 跨端兼容：非微信环境无 uni.getLocation 或字段结构不同
    if (typeof uni === 'undefined' || typeof uni.getLocation !== 'function') {
      resolve(null)
      return
    }
    // @ts-ignore - 微信小程序 getLocation（type=gcj02 与后端坐标体系一致）
    uni.getLocation({
      type: 'gcj02',
      altitude: false,
      success: (res: { latitude: number; longitude: number }) => {
        if (typeof res.latitude === 'number' && typeof res.longitude === 'number') {
          resolve({ lat: res.latitude, lng: res.longitude })
        } else {
          resolve(null)
        }
      },
      fail: () => resolve(null),
    })
  })
}

/** Haversine：两 GCJ-02 坐标直线距离（米），手机本地算，无需上报用户位置 */
export function haversineMeters(a: UserLocation, b: UserLocation): number {
  const R = 6371000
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return Math.round(2 * R * Math.asin(Math.sqrt(h)))
}
