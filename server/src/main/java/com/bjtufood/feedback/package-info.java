/**
 * 用户反馈模块（package {@code com.bjtufood.feedback}）。
 * <p>
 * <b>职责</b>：反馈提交（含举报）、管理端列表与处理回执。
 * <p>
 * <b>依赖方向</b>：common、auth（用户读契约与归属迁移事件）、dish、moderation、notification
 * <p>
 * <b>对外契约</b>：FeedbackService（migrateOwnership 等写契约）
 * <p>
 * <b>领域事件</b>：无（归属迁移由 auth 的 UserOwnershipMigratedEvent 驱动本域监听）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.feedback;
