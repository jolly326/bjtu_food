/**
 * API 层共享工具（消除跨模块重复 + 统一图片/分页归一化）。
 * 被 dish / review / canteen / moment 等模块 import，避免每文件各写一份
 * recordsOf / normalizeImages（既重复又易产生行为分叉）。
 */
import { getImageUrl } from '@/utils/image'

/** 后端分页返回形态：可能是平铺数组，或 { records | list, total } */
export type PageLike<T> = T[] | { records?: T[]; list?: T[]; total?: number }

/** 从分页响应提取列表（任意形态均安全降级为空数组） */
export function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || value.list || []
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
