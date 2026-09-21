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

/** 后端分页返回形态：可能是平铺数组，或 { records, total } */
type PageLike<T> = T[] | { records?: T[]; total?: number }

/**
 * 分页响应统一结构（{ records, total, page, pageSize }）。
 * 注（2026-09-21 契约精简，见 docs/project_spec.md §7.33）：原过渡期兼容字段 `list`
 * 已随后端 `PageResult` 一并删除——服务端只输出 `records`，消费方只读 `records`。
 */
export interface PageResult<T> {
  records?: T[]
  total?: number
  page?: number
  pageSize?: number
}

/**
 * API 层 `get<T>` 的**定型分页载体**（MP-08）：裸数组或 { records, total } 二选一。
 * 取代各 api 模块里的 `get<any>` / `recordsOf<any>`——后端 JSON 未按 OpenAPI 逐字段建模，
 * 但「响应是分页结构」这一层是确定的，足以定型，不必退到 any。
 */
export type RawPage = PageResult<RawRow> | RawRow[]

/** 从分页响应提取列表（任意形态均安全降级为空数组） */
export function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || []
}

/** 从分页响应提取总数（缺省回退列表长度） */
export function totalOf(value: PageLike<any> | undefined | null): number {
  if (!value) return 0
  if (Array.isArray(value)) return value.length
  return typeof value.total === 'number' ? value.total : recordsOf(value).length
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
