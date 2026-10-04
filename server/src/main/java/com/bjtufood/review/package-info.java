/**
 * 评价模块（package {@code com.bjtufood.review}）。
 * <p>
 * <b>职责</b>：评价提交、重新评价、删除、隐藏、审核，以及只读查询契约。
 * <p>
 * <b>依赖方向</b>：common、auth（用户读契约与事件）、dish（菜品读契约与删除事件）、moderation、notification（事件）
 * <p>
 * <b>对外契约</b>：ReviewService（写）、ReviewQueryService（只读，零业务依赖可安全注入）、StallAvgRatingVO
 * <p>
 * <b>领域事件</b>：ReviewSubmittedEvent（评分重算，由 dish 异步订阅）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.review;
