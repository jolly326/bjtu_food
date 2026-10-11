/**
 * 用户提交与字典模块（package {@code com.bjtufood.feedback}）。
 * <p>
 * <b>职责</b>：三类用户提交中的**意见反馈**（`feedback`）与**评价举报**（`report`）的提交 /
 * 管理端列表 / 处置归档请求，以及两个分类字典（`feedback_option` / `report_option`）的维护与公开下发。
 * 处置状态机（`moderation_task`）归 {@code moderation} 域，本域只经其服务契约调用。
 * <p>
 * <b>依赖方向</b>：common、auth（用户读契约与归属迁移事件）、review（被举报评价的只读契约）、
 * moderation（内容安全检测 + 处置任务写契约）、notification（站内回执经处置任务域投递）
 * <p>
 * <b>对外契约</b>：FeedbackService / ReportService / FeedbackOptionService / ReportOptionService
 * <p>
 * <b>领域事件</b>：无（归属迁移由 auth 的 UserOwnershipMigratedEvent 驱动本域监听）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.feedback;
