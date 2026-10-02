/**
 * 端上展示文案常量（唯一真源）。
 *
 * 收敛动机：同一句话在多处各写一份（如「未完成校园认证」原在「我的」页与「我的主页」各定义一次），
 * 改文案要全局搜。归口至此，调用点只引用常量。
 */
/** 默认昵称：后端昵称出参为空时的兜底（认证态） */
export const DEFAULT_NICKNAME = '食客'
/** 游客态默认昵称：后端建号昵称为空时的兜底 */
export const GUEST_NICKNAME = '游客'
/** 游客态身份副行（身份卡第二行；认证态副行展示校园邮箱） */
export const GUEST_SUB_TEXT = '未完成校园认证'
/** 只读字段空值占位（校园邮箱等未填写时行内不空白） */
export const EMPTY_FIELD_TEXT = '--'
/** 评价作者昵称兜底（历史匿名评价 / 出参缺省） */
export const ANONYMOUS_AUTHOR = '匿名用户'
/** 「评价已不存在」收尾提示（后端 4001；该码重试无意义） */
export const REVIEW_GONE_TEXT = '评价已不存在'

/**
 * 删除评价的二次确认弹窗文案（两条删除路径共用）。
 *
 * <p><b>为何共用</b>：删除评价有两条入口 —— 菜品详情页评价区（`useDishReviewCore`）与
 * 「我的主页」评价列表（`my-reviews/index.vue`）。两者**后续动作不同**（前者需重拉列表 +
 * 重拉详情以刷新评分，后者只需本地移除），但**用户看到的是同一个动作**，文案必须逐字一致；
 * 各写一份时改文案必然漏改一处。
 */
export const CONFIRM_DELETE_REVIEW = {
  title: '删除评价',
  content: '确定删除这条评价吗？删除后不可恢复。',
  confirmText: '删除',
} as const

/** 删除评价成功提示（配套 {@link CONFIRM_DELETE_REVIEW}，两处删除路径共用） */
export const TOAST_REVIEW_DELETED = '评价已删除'
