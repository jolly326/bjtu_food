/**
 * 分页口径（**全站单一真源**）。
 *
 * <p><b>为何单独成文件</b>：此前「每页几条」这个决定散落在 8 处 ——
 * `api/dish.ts`、`api/review.ts`（两处）、`api/notify.ts` 各写 `?? 20`，
 * `stores/dish.ts`、`pages/detail/dish/useDishPage.ts`、`pages/find/index.vue`
 * 各自定义一个常量，`composables/usePagedList.ts` 又给了一个默认值。
 * 同一个「缺省页大小」在 4 个 api 文件里各写一份，改一处漏三处就会出现
 * 「列表首屏条数不一致」这类难查的问题。
 *
 * <p><b>注意</b>：各页面实际的每页条数（首页 10 / 评价 10 / 搜索 20）是**不同的业务决定**，
 * 不是重复 —— 本文件收敛的是「定义位置」，让四个决定并排可见、可一处审阅，
 * 而不是把它们强行统一成同一个值。
 *
 * <p><b>契约提醒</b>：后端 `PageResult` 只下发 `records`（无 `total`），
 * 故所有列表的结束判据都是「本页返回条数 < 使用的 pageSize」。
 */

/** 通用每页条数：api 层各列表接口的缺省值，也是 `usePagedList` 的默认页大小 */
export const DEFAULT_PAGE_SIZE = 20

/** 首页瀑布流每页条数（首屏更快出内容） */
export const HOME_PAGE_SIZE = 10

/** 菜品详情评价区每页条数 */
export const REVIEW_PAGE_SIZE = 10

/** 搜索结果每页条数 */
export const RESULT_PAGE_SIZE = 20

/**
 * 列表渲染封顶页数（通知 / 我的评价 / 详情评价区共用）。
 * 这些列表无虚拟化，深翻会让节点数无上限增长（低端机掉帧、内存攀升）；
 * 达此页数后置「已到底」停止追加（400 条 @ 每页 20 / 200 条 @ 每页 10，实际不可感知）。
 */
export const MAX_LIST_PAGES = 20
