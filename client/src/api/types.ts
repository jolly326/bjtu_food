/**
 * 契约类型别名（学生端 api 层唯一出口）。
 *
 * 类型全部来自契约产物 `@/types/generated/api.d.ts`；**不手写 DTO**。
 * 每个别名形如 `export type XxxOut = components['schemas']['XxxOut']`，
 * 供 `check:contract` 校验「re-export 名与契约一致、无悬空导出」。
 */

import type { components } from '@/types/generated/api'

export type PosterOut = components['schemas']['PosterOut']
export type DishCardOut = components['schemas']['DishCardOut']
export type RecordOut = components['schemas']['RecordOut']
export type IssueOptionOut = components['schemas']['IssueOptionOut']

/** 学生端食堂字典（运营端同名 schema 被 FastAPI 加了模块前缀，故此处按契约字段内联） */
export type CanteenOut = { id: number; name: string }
