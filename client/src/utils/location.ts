/**
 * 定位工具（首页左上角定位）
 *
 * 方案 C：真实定位（uni.getLocation）+ 手动选择兜底。
 * - 定位成功：拿到 GCJ-02 经纬度 → 缓存 → 供首页推荐按距离排序
 * - 授权拒绝/失败：降级为手动选择（校区），展示文字
 * - 已定位后再点：重新定位（供切换）
 */

export interface LocationInfo {
  /** 展示名称（校区/食堂名），如「北京交通大学」 */
  name: string
  /** GCJ-02 纬度（用于距离排序），未定位为 null */
  lat: number | null
  /** GCJ-02 经度（用于距离排序），未定位为 null */
  lng: number | null
}

const KEY = 'home_location'

/** 校园预置坐标（GCJ-02）：用于手动选择时的名称与定位匹配 */
export const CAMPUS_PRESETS: { name: string; lat: number; lng: number }[] = [
  { name: '北京交通大学', lat: 39.9538, lng: 116.3354 },
]

const DEFAULT_LOCATION: LocationInfo = { name: '北京交通大学', lat: null, lng: null }

/** 读取缓存的定位信息（无缓存返回默认「北京交通大学」） */
export function getCachedLocation(): LocationInfo {
  try {
    const raw = uni.getStorageSync(KEY)
    if (raw && typeof raw === 'object' && typeof raw.name === 'string') {
      return {
        name: raw.name || DEFAULT_LOCATION.name,
        lat: typeof raw.lat === 'number' ? raw.lat : null,
        lng: typeof raw.lng === 'number' ? raw.lng : null,
      }
    }
  } catch {
    /* 存储不可用时忽略 */
  }
  return { ...DEFAULT_LOCATION }
}

function saveLocation(info: LocationInfo) {
  try {
    uni.setStorageSync(KEY, info)
  } catch {
    /* 忽略写入失败 */
  }
}

/**
 * 请求真实定位（微信授权弹窗）。
 * @returns 成功返回 {lat, lng}；失败/拒绝返回 null
 */
export function requestGeoLocation(): Promise<{ lat: number; lng: number } | null> {
  return new Promise(resolve => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: false,
      success: res => {
        const lat = typeof res.latitude === 'number' ? res.latitude : NaN
        const lng = typeof res.longitude === 'number' ? res.longitude : NaN
        if (Number.isFinite(lat) && Number.isFinite(lng)) {
          resolve({ lat, lng })
        } else {
          resolve(null)
        }
      },
      fail: () => resolve(null),
    })
  })
}

/**
 * 定位主流程（首页 loc-bar 点击时调用）：
 * 1. 请求定位 → 成功：更新经纬度 + 校区名，缓存，返回新位置
 * 2. 失败/拒绝：若从未定位过则保持默认（返回 null 表示无变化）；已定位过则保持旧位置
 * @returns 定位成功且位置有变化时返回 LocationInfo；无变化返回 null
 */
export async function refreshLocation(): Promise<LocationInfo | null> {
  const geo = await requestGeoLocation()
  const current = getCachedLocation()
  if (geo) {
    const name = matchCampusName(geo.lat, geo.lng) || current.name || DEFAULT_LOCATION.name
    const next: LocationInfo = { name, lat: geo.lat, lng: geo.lng }
    saveLocation(next)
    return next
  }
  return null
}

/** 把经纬度匹配到最近的预置校区（简单取最近），无匹配返回 null */
function matchCampusName(lat: number, lng: number): string | null {
  let best: string | null = null
  let bestDist = Infinity
  for (const p of CAMPUS_PRESETS) {
    const d = haversine(lat, lng, p.lat, p.lng)
    if (d < bestDist) {
      bestDist = d
      best = p.name
    }
  }
  // 超出 3km 视为不在校园内，仍显示最近校区但可后续优化
  return best
}

/** haversine 距离（米） */
function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371000
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

function toRad(deg: number): number {
  return (deg * Math.PI) / 180
}

/** 手动选择校区（展示用）：当前仅一个校区，直接返回默认 */
export function pickCampusManually(): LocationInfo {
  return { ...DEFAULT_LOCATION, name: CAMPUS_PRESETS[0].name }
}
