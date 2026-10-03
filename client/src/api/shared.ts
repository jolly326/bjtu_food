/**
 * API 层共享工具（消除跨模块重复 + 统一图片 / 分页归一化）。
 * 被 dish / review / canteen 等模块 import，避免各文件各写一份 `recordsOf` / `normalizeImages`。
 */
import { getImageUrl } from '@/utils/image'

// @ts-ignore - 生成文件由 openapi-typescript 产出，随 openapi.json 一并入库
import type { components } from '@/types/generated/api'

/**
 * 后端契约的强类型出口（**必须用这些类型，不得手写契约副本**，否则「后端改字段 → 端上 type-check 变红」失效）。
 *
 * 逐字段具名 re-export 而非 `type Api = components['schemas']`：TS 4.9 既不支持
 * 别名取索引后的点号访问（TS2713/TS2702），也不支持 `interface X extends Y['K']`（TS2499），
 * 故采用显式 re-export。新增后端 VO 时在下方补一行即可。
 *
 * 契约真源始终是后端 VO，本文件只是镜像：刷新用 `npm run gen:api:fresh`（需先启动后端）。
 */
export type DishListItemVO = components['schemas']['DishListItemVO']
export type DishDetailVO = components['schemas']['DishDetailVO']
export type DishAttributeItem = components['schemas']['DishAttributeItem']
export type DishAttributeEditVO = components['schemas']['DishAttributeEditVO']
export type DishViewVO = components['schemas']['DishViewVO']
export type GuessLikeVO = components['schemas']['GuessLikeVO']
export type ReviewVO = components['schemas']['ReviewVO']
export type MyReviewVO = components['schemas']['MyReviewVO']
export type NotificationVO = components['schemas']['NotificationVO']
export type UnreadCountVO = components['schemas']['UnreadCountVO']
export type UserInfoVO = components['schemas']['UserInfoVO']
export type LoginVO = components['schemas']['LoginVO']
export type BannerVO = components['schemas']['BannerVO']
export type ReportReasonVO = components['schemas']['ReportReasonVO']
export type UploadResultVO = components['schemas']['UploadResultVO']
export type ReviewCreatedVO = components['schemas']['ReviewCreatedVO']

/** 后端分页返回形态：可能是平铺数组，或 `{ records }` */
type PageLike<T> = T[] | { records?: T[] }

/**
 * 分页响应统一结构 **只有 `records`**（与服务端 `PageResult` 一致，由 `PageResultContractTest` 锁定）。
 * 页码 / 每页条数由请求侧掌握、不回传；列表结束判据 = **本页条数 < `pageSize`** ⇒ 端上不读任何总数型字段。
 */
export interface PageResult<T> {
  records?: T[]
}

/** API 层 `get<T>` 的定型分页载体：裸数组或 `{ records }` 二选一（元素类型由调用方给出，通常为上方 `XxxVO`） */
export type RawPage<T = unknown> = PageResult<T> | T[]

/** 从分页响应提取列表（任意形态均安全降级为空数组） */
export function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || []
}

/**
 * 图片字段归一化（全模块统一，健壮版）：
 * - 数组：逐项转绝对地址
 * - JSON 字符串：解析后递归（兼容 `"[...]"` 存法）
 * - `|||` 分隔字符串：按分隔符拆分（历史 DB 存法）
 * - 普通字符串：单图
 * 空 / 非法一律返回 `[]`，绝不抛错。
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
