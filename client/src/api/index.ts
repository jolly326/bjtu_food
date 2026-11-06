/**
 * API 封装层（按域拆分，此处为统一出口）。
 *
 * 类型全部来自契约产物 `@/types/generated/api.d.ts`（Pydantic -> OpenAPI -> openapi-typescript），
 * 不手写 DTO；字段口径见 docs/api/。
 */

export { dishApi } from './dish'
export { feedbackApi } from './feedback'
export type { DishLocationVO, DishVO, RecommendOut } from './types'
export type { FeedbackPayload, FeedbackType } from './feedback'
