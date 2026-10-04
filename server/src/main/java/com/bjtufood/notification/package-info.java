/**
 * 站内通知模块（package {@code com.bjtufood.notification}）。
 * <p>
 * <b>职责</b>：通知投递、已读标记、未读计数，以及账号事件驱动的行数据清理。
 * <p>
 * <b>依赖方向</b>：common、auth（订阅账号事件）
 * <p>
 * <b>对外契约</b>：NotificationService（notify 投递，异步独立事务）、NotificationCmd
 * <p>
 * <b>领域事件</b>：NotificationAccountCleanListener（订阅 auth 的账号事件）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验，详见 {@code docs/architecture.md}。
 */
package com.bjtufood.notification;
