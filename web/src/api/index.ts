/** 运营端 API 封装（按域拆分，此处为统一出口；字段口径见 docs/api/web/）。 */

export { sessionApi } from './auth'
export { canteenApi } from './canteen'
export { stallApi } from './stall'
export { dishApi } from './dish'
export { feedbackApi } from './feedback'
export { upload } from './images'
export type {
  CanteenOut,
  DishBody,
  DishLocationInput,
  DishLocationOut,
  DishOut,
  FeedbackOut,
  FeedbackType,
  Page,
  StallBody,
  StallOut,
} from './types'
