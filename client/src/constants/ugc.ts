/**
 * UGC 配图相关常量（评价 / 反馈 / 纠错共用）。
 *
 * <p><b>为何单独成文件</b>：`UGC_IMAGE_MAX` 此前在 `pages/feedback/IssueForm`、
 * `pages/detail/dish/ReviewComposer`、`pages/correction/CorrectionForm`、
 * `pages/correction/useCorrection` 四处各硬编码一份 `3`，与服务端 `@Size(max = 3)` 的契约靠人工同步。
 * 任一处改错就会出现「端上让选 3 张、提交被后端拒」的错位。现收敛为单点。
 */

/**
 * 单条 UGC 的配图张数上限（**评价 / 反馈 / 举报**，**不含纠错**）。
 *
 * <b>契约同源</b>：服务端 `FeedbackReq` / `ReportReq` 的 `@Size(max = 3)`；
 * 改动本常量须同步服务端校验，否则会出现「端上能选、提交被拒」。
 */
export const UGC_IMAGE_MAX = 3

/**
 * **菜品纠错**的配图上限（张）—— 与评价 / 反馈**脱钩**，为 5。
 *
 * <p><b>为何纠错更宽</b>：纠错要说明「现场实际是什么样」，常需「菜品 + 价签 + 档口牌」等多张佐证，
 * 3 张不够用（见 `docs/web/README.md` 待办 #4）。
 *
 * <p><b>契约同源</b>：服务端 `CorrectionConst.IMAGE_MAX`（2026-10-03 由 3 改为 5）。
 */
export const CORRECTION_IMAGE_MAX = 5
