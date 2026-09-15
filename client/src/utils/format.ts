/**
 * 通用展示格式化（非金额：金额一律走 utils/money）。
 *
 * 距离文案唯一口径：米 → 「米 / 公里」自适应。
 * 收敛前曾有三份逐字重复实现（DishCard / FindResults / useDishPage），
 * 其中详情页那份缺四舍五入与脏数据守卫，现统一到本函数。
 */

/**
 * 距离文案（入参单位：米，由前端基于定位本地计算，服务器不算）。
 * - 脏数据（null / NaN / 非有限 / 负数）返回空串，由调用方决定隐藏或兜底文案；
 * - 超大距离（>999km，应为异常坐标）截断为 `>999km`；
 * - ≥1000m 折公里保留一位小数，<1000m 取整到米。
 */
export function formatDistance(m: number | null | undefined): string {
  if (m == null || !Number.isFinite(m) || m < 0) return ''
  if (m > 999000) return '>999km'
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`
}
