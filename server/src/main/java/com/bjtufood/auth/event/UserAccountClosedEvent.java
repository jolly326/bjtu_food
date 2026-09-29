package com.bjtufood.auth.event;

/**
 * 账号注销事件（P0-1 跨域写侧解耦）。
 * <p>
 * 发布方：{@code AuthServiceImpl.deleteAccount}（auth 域，事务内发布，发布点即原
 * {@code notificationMapper.delete(...)} 的位置）。
 * 订阅方：{@code notify.event.NotificationAccountCleanListener} —— 硬删该用户全部站内消息。
 * <p>
 * <b>唯一订阅方的原因是口径本身如此</b>：注销对业务内容的处理是「保留 + 匿名化展示」——
 * review / user_feedback 的行与 {@code user_id} 一律不动（正文有内容价值），展示昵称由
 * join user 实时取「已注销用户」（user 行软删：{@code status='deleted'}、昵称改写、身份列置 NULL）。
 * 只有 notification 属「账号维度的过程性数据、注销后无任何读取方」，才随注销物理删除。
 * <p>
 * <b>为何用事件</b>：auth 直接注入 {@code NotificationMapper} 属跨模块写他域表；改注入
 * NotificationService 又会与 notify ← 各业务域的依赖叠加。写侧跨域动作一律走事件，
 * 与既有 {@code ReviewSubmittedEvent → RatingUpdateListener} 同构。
 * 同步监听（无 {@code @TransactionalEventListener}）：仍在注销事务内执行，失败整体回滚。
 *
 * @param userId 被注销账号ID
 */
public record UserAccountClosedEvent(Long userId) {
}


