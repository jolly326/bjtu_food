/**
 * UGC 配图相关常量（评价 / 反馈 / 菜品问题反馈共用）。
 *
 * <p><b>为何单独成文件</b>：`UGC_IMAGE_MAX` 全站单点定义，与服务端 `FeedbackReq` / `ReportReq` 的
 * `@Size(max = 3)` 契约同源，避免「端上让选 3 张、提交被后端拒」的错位。
 */

/**
 * 单条 UGC 的配图张数上限（**评价 / 反馈 / 举报 / 菜品问题反馈全部一致**）。
 *
 * <b>契约同源</b>：服务端 `UgcImageValidator.MAX_IMAGES`（= 3）与各请求对象的 `@Size(max = 3)`；
 * 改动本常量须同步服务端校验，否则会出现「端上能选、提交被拒」。
 */
export const UGC_IMAGE_MAX = 3

/**
 * **菜品问题反馈 · `field` 型（信息有误）**的配图上限（张）= 3，与评价 / 反馈**同档**。
 *
 * <p><b>为何不再单设更宽的档</b>：`field` 型与评价 / 反馈**同档**：UGC 配图统一 ≤3 张 ——
 * **全站 UGC ≤3 张** —— 上限分档会让「同一种图片行为有不同上限」难以解释，且 3 张已足够表达
 * 「菜品 + 价签 + 档口牌」。
 *
 * <p><b>契约同源</b>：服务端 `CorrectionConst.IMAGE_MAX`（值为 3）。
 */
export const CORRECTION_IMAGE_MAX = 3

/**
 * **菜品问题反馈 · `gone` 型（已经下架）**的配图上限（张）= 3。
 *
 * <p><b>语义</b>：gone 是「**选填补充**」—— 用户只是路过拍一张当前窗口，用来表达
 * 「变成了别的菜 / 换窗口了」，1~3 张足够。
 *
 * <p><b>契约同源</b>：服务端 `CorrectionConst.GONE_IMAGE_MAX`（值为 3）。
 */
export const GONE_IMAGE_MAX = 3
