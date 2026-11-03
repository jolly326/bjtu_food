/** 运营端 API 封装（按域拆分，此处为统一出口；字段口径见 docs/api/web/）。 */

export { sessionApi } from './session'
export { canteenApi } from './canteen'
export { stallApi } from './stall'
export { dishApi } from './dish'
export { feedbackApi } from './feedback'
export { upload } from './request'
export type {
  CanteenOut,
  DishBody,
  DishOfferingInput,
  DishOfferingOut,
  DishOut,
  FeedbackOut,
  FeedbackType,
  Page,
  StallBody,
  StallOut,
} from './types'
