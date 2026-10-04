package com.bjtufood.auth.event;

/**
 * 账号归属迁移事件（P0-1 跨域写侧解耦）：把 {@code fromUserId} 账号下的业务数据改挂到
 * {@code toUserId}（触发场景 = 学号邮箱认证时的「替换绑定」与「历史邮箱注册账号并入当前微信」）。
 * <p>
 * 发布方：{@code AuthServiceImpl.migrateOwnership}（auth 域，事务内发布，等价于原三处内联写库的位置）。
 * 订阅方（各域自理本域表，auth 不再持有他域 Mapper）：
 * <ul>
 *   <li>{@code review.event.ReviewOwnershipListener} —— 先清理冲突行（to 已评价过的同菜品，
 *       保留 to 的记录、删 from 的），再改 {@code review.user_id}；唯一键
 *       {@code uk_review_user_dish} 决定了必须先清理后改写，该知识留在 review 域内</li>
 *   <li>{@code feedback.event.FeedbackOwnershipListener} —— 改 {@code user_feedback.user_id}</li>
 *   <li>{@code notify.event.NotificationAccountCleanListener} —— 改 {@code notification.user_id}</li>
 * </ul>
 * 同步监听（无 {@code @TransactionalEventListener}）：与 {@code verifyEmail} 外层事务同进同退，
 * 任一环节失败整体回滚，与原实现一致。
 * <p>
 * 注：{@code dish.created_by} 列已随 零消费退役，故 dish 域不参与归属迁移。
 *
 * @param fromUserId 迁出账号ID（旧微信 / 历史邮箱注册账号）
 * @param toUserId   迁入账号ID（当前微信）
 */
public record UserOwnershipMigratedEvent(Long fromUserId, Long toUserId) {
}

