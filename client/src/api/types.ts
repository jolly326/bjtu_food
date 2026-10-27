/**
 * 契约类型别名（学生端 api 层唯一出口）。
 *
 * 类型全部来自契约产物 `@/types/generated/api.d.ts`；**不手写 DTO**。
 * 每个别名形如 `export type XxxVO = components['schemas']['XxxVO']`，
 * 供 `check:contract` 校验「re-export 名与契约一致、无悬空导出」。
 */

import type { components } from '@/types/generated/api'

export type DishVO = components['schemas']['DishVO']
export type DishLikeOut = components['schemas']['DishLikeOut']
export type IssueOptionOut = components['schemas']['IssueOptionOut']
