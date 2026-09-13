/**
 * API 层共享工具（消除跨模块重复 + 统一图片/分页归一化）。
 * 被 dish / review / canteen 等模块 import，避免每文件各写一份
 * recordsOf / normalizeImages（既重复又易产生行为分叉）。
 */
import { getImageUrl } from '@/utils/image'

/**
 * 后端响应行（归一化边界载体，R4 收敛）。
 * 后端 JSON 未按 OpenAPI 逐字段建模时，各模块 toXxx 归一化函数统一以 `RawRow` 作入参
 * （替代散落的裸 any 形参），字段读取保持与 any 相同的宽松语义；本类型禁止流出 api/ 层。
 */
export type RawRow = Record<string, any>

/** 后端分页返回形态：可能是平铺数组，或 { records | list, total } */
type PageLike<T> = T[] | { records?: T[]; list?: T[]; total?: number }

/** 分页响应统一结构（{ list | records, total, page, pageSize }；F2 收敛自 notify/activity 等私有定义） */
export interface PageResult<T> {
  list?: T[]
  records?: T[]
  total?: number
  page?: number
  pageSize?: number
}

/** 从分页响应提取列表（任意形态均安全降级为空数组） */
export function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || value.list || []
}

/**
 * 从分页响应提取列表——兼容裸数组 / { list } / { records } 三形态
 * （统一版取 notify/activity 等私有 listOf 行为超集；activity 曾支持裸数组）。
 */
export function listOf<T>(value: PageResult<T> | T[] | undefined | null): T[] {
  return recordsOf<T>(value)
}

/** 从分页响应提取总数（缺省回退列表长度） */
export function totalOf(value: PageLike<any> | undefined | null): number {
  if (!value) return 0
  if (Array.isArray(value)) return value.length
  return typeof value.total === 'number' ? value.total : recordsOf(value).length
}

/** 统一布尔归一化（兼容 true / 1 / '1'） */
export function normalizeBoolean(value: unknown): boolean {
  return value === true || value === 1 || value === '1'
}

/**
 * 图片字段归一化（健壮版，全模块统一）：
 * - 数组：逐项转绝对地址
 * - JSON 字符串：解析后递归（兼容 "[...]" 存法）
 * - "|||" 分隔字符串：按分隔符拆分（历史 DB 存法）
 * - 普通字符串：单图
 * 空/非法：返回 []，绝不抛错。
 */
export function normalizeImages(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && item.length > 0).map(getImageUrl)
  }
  if (typeof value !== 'string' || !value.trim()) return []
  const text = value.trim()
  try {
    const parsed = JSON.parse(text)
    return Array.isArray(parsed) ? normalizeImages(parsed) : [getImageUrl(text)]
  } catch {
    return text.split('|||').map(item => item.trim()).filter(Boolean).map(getImageUrl)
  }
}

/** 取行首图（normalizeImages 后取首项；兼容 images/image/icon 字段形态；F3 上提自 canteen 私有版） */
export function firstImage(raw: RawRow | null | undefined): string {
  if (!raw) return ''
  return normalizeImages(raw.images ?? raw.image ?? raw.icon)[0] || ''
}
