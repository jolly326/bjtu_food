/**
 * API 层共享工具（消除跨模块重复 + 统一图片/分页归一化）。
 * 被 dish / review / canteen 等模块 import，避免每文件各写一份
 * recordsOf / normalizeImages（既重复又易产生行为分叉）。
 *
 * <p>：本文件顶部新增后端契约的**具名 re-export**
 * （如 {@link DishListItemVO}，来自 `types/generated/api.d.ts`，由后端 OpenAPI 契约生成）。
 * 新代码**必须**用这些强类型而非 {@link RawRow}，
 * 使「后端改字段 → 端上 `type-check` 变红」成立。
 */
import { getImageUrl } from '@/utils/image'

/**
 * **生成类型**（由 `client/openapi.json` → `openapi-typescript` 产出，勿手改）。
 *
 * <p>**用法**：本文件下方逐个 re-export 的 VO 类型（名与后端 VO 同名），如
 * {@link DishListItemVO} / {@link UserInfoVO} / {@link ReviewVO}。
 *
 * <p><b>刷新</b>：启动后端后 `npm run gen:api:fresh`。
 * 契约真源始终是后端 VO，本文件只是其镜像，不引入手工维护的副本。
 */
// @ts-ignore - 生成文件由 openapi-typescript 产出，随 openapi.json 一并入库
import type { components } from '@/types/generated/api'

/**
 * 后端契约的强类型出口。
 *
 * <p><b>为何是逐字段 re-export 而非 {@code type Api = components['schemas']}</b>：
 * TypeScript 4.9（client 当前版本）既不支持「type 别名取索引后再用 {@code X.A} 点号访问」
 * （TS2713/TS2702：{@code Api} is a type, but not a namespace），
 * 也不支持 {@code interface X extends Y['K']}（TS2499：interface 只能继承标识符）。
 * 故采用 4.9 的标准做法——**显式具名 re-export**：既拿到字段级强类型，
 * 又保留可读的具名导入形式。
 *
 * <p>新增后端 VO 时在本文件补一行 re-export 即可；
 * 字段名与后端 VO 同名，由 {@code openapi-typescript} 保证零漂移。
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

/**
 * 后端响应行（**归一化边界的兜底载体**）。
 *
 * <p><b>用途收窄</b>：仅供**尚无生成类型覆盖**的场景使用
 * （如后端新增端点但契约未刷新）。凡有对应 VO 的，**一律用本文件 re-export 的强类型**，
 * 否则契约漂移又会无感知发生——`ApiContractTest` 会对新增的 `RawRow` 用法报警。
 *
 * <p>后端 JSON 未按 OpenAPI 逐字段建模时，各模块 toXxx 归一化函数可暂以 `RawRow` 作入参；
 * 本类型禁止流出 api/ 层。
 */
export type RawRow = Record<string, any>

/** 后端分页返回形态：可能是平铺数组，或 `{ records }` */
type PageLike<T> = T[] | { records?: T[] }

/**
 * 分页响应统一结构 **只有 `records` 一项**（`PageResult<T>{ records }`）。
 *
 * 页码 / 每页条数由**请求侧掌握、不回传**；分页列表的结束判据 =
 * **本页返回条数 < `pageSize`**，故端上不读取任何总数型字段。
 *
 * <p>与服务端 {@code com.bjtufood.common.result.PageResult} 一致，
 * 由 {@code PageResultContractTest} 锁定。
 */
export interface PageResult<T> {
  records?: T[]
}

/**
 * API 层 `get<T>` 的**定型分页载体**：裸数组或 `{ records }` 二选一。
 *
 * <p>取代各 api 模块里的 `get<any>`——「响应是分页结构」这一层是确定的，
 * 配合本文件 re-export 的元素类型（`XxxVO`）即可获得完整的字段级类型保障。
 *
 * <p>（历史说明）此前 `RawPage` 的元素类型是 {@link RawRow}，因而后端改字段端上无感知；
 * 现元素类型由调用方给出，通常为本文件 re-export 的 `XxxVO`。
 */
export type RawPage<T = unknown> = PageResult<T> | T[]

/** 从分页响应提取列表（任意形态均安全降级为空数组） */
export function recordsOf<T>(value: PageLike<T> | undefined | null): T[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  return value.records || []
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
