package com.bjtufood.notification.service;

import com.bjtufood.common.result.PageResult;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.dto.NotificationVO;

/**
 * 消息通知服务接口
 * <p>
 * P3/ARCH-008：学生端「我的通知」三端点（列表/未读数/单条已读）逻辑
 * 自 NotificationController 下沉至本服务，Controller 只留参数与响应包装。
 */
public interface NotificationService {

    /**
     * 写入一条通知
     *
     * @param notification 通知实体
     */
    /**
     * 写入一条通知（跨域投递契约）
     * <p>
     * 入参为 {@link NotificationCmd} 而非 {@code notify.entity.Notification}：
     * 架构收口 P0-1，feedback / correction 等投递方不再 import notify 的实体，
     * 实体只在 notify 内部构造（{@code isRead} 由实现侧统一置 0，调用方无需关心）。
     *
     * @param cmd 通知入参（type 取 {@code NotificationConst.TYPE_*}）
     */
    void notify(NotificationCmd cmd);

    /**
     * 我的通知列表：按创建时间倒序，可选 isRead 过滤，分页归一化
     *
     * @param userId   接收用户ID（SecurityUtil 取当前用户，不信任前端）
     * @param isRead   已读过滤：true=仅已读 / false=仅未读（null=全部）
     * @param page     页码（&lt;1 时回退 1）
     * @param pageSize 每页条数（&lt;1 回退 10，&gt;100 截断 100）
     */
    PageResult<NotificationVO> listMy(Long userId, Boolean isRead, int page, int pageSize);

    /**
     * 未读通知计数（驱动首页红点）
     */
    long countUnread(Long userId);

    /**
     * 单条标记已读（归属校验：通知不存在或非本人时静默成功，幂等且不暴露他人通知存在性）
     */
    void markRead(Long userId, Long notificationId);

    /**
     * 全部标记已读（spec §7.18）：将当前用户全部未读通知一次性置为已读。
     * <p>
     * 单条批量 UPDATE（{@code WHERE user_id = ? AND is_read = 0}），不逐条循环；
     * 幂等：无未读时返回 0，不报错。
     *
     * @param userId 当前登录用户ID（SecurityUtil 取，不信任前端）
     * @return 本次置为已读的条数（更新 0 条表示本就无未读）
     */
    int markAllRead(Long userId);
}
