/**
 * 图片 URL 工具函数
 *
 * 当前（MVP Mock 阶段）：
 * - image 字段存本地静态路径
 * - getImageUrl() 直接透传，由 uni-app 自动解析静态资源
 *
 * 后续切换到真实后端后：
 * - 后端 DishVO.image 存相对路径（如 "/uploads/2024/01/xxx.jpg"）
 * - 把下面 IMAGE_BASE 的注释去掉，把 mock 改成 API 调用即可
 */

// const IMAGE_BASE = 'http://localhost:8080/api' // 切换真实后端时取消注释

/**
 * 构造完整的图片 URL
 *
 * @param path 图片路径
 * @returns 可使用的图片 URL
 *
 * @example
 * getImageUrl('/static/icons/food.svg')  // → '/static/icons/food.svg'
 * getImageUrl('')                         // → ''
 * getImageUrl(null)                       // → ''
 */
export function getImageUrl(path?: string | null): string {
  if (!path) return ''
  // 已经是完整 URL
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  // Mock 阶段：本地静态资源，直接返回（uni-app 自动处理 /static/ 路径）
  return path
}
