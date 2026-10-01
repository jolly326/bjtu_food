/**
 * UGC 配图相关常量（评价 / 反馈 / 纠错共用）。
 *
 * <p><b>为何单独成文件</b>：`UGC_IMAGE_MAX` 此前在 `pages/feedback/IssueForm`、
 * `pages/detail/dish/ReviewComposer`、`pages/correction/CorrectionForm`、
 * `pages/correction/useCorrection` 四处各硬编码一份 `3`，与服务端 `@Size(max = 3)` 的契约靠人工同步。
 * 任一处改错就会出现「端上让选 3 张、提交被后端拒」的错位。现收敛为单点。
 */

/**
 * 单条 UGC 的配图张数上限。
 *
 * <b>契约同源</b>：服务端 `FeedbackReq` / `ReportReq` 的 `@Size(max = 3)`、`DishCorrectionReq` 的
 * 图片校验均为 3（见 `docs/client/feature/client-意见反馈.md`、`client-举报评价.md`、`client-菜品纠错.md`）。
 * 改动本常量须同步服务端校验，否则会出现「端上能选、提交被拒」。
 */
export const UGC_IMAGE_MAX = 3
