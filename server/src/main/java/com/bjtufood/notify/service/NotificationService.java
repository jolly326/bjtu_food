package com.bjtufood.notify.service;

import com.bjtufood.common.result.PageResult;
import com.bjtufood.notify.dto.NotificationVO;
import com.bjtufood.notify.entity.Notification;

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
    void notify(Notification notification);

    /**
     * 我的通知列表：按创建时间倒序，可选 isRead 过滤，分页归一化
     *
     * @param userId   接收用户ID（SecurityUtil 取当前用户，不信任前端）
     * @param isRead   已读过滤：0/1（null=全部）
     * @param page     页码（&lt;1 时回退 1）
     * @param pageSize 每页条数（&lt;1 回退 10，&gt;100 截断 100）
     */
    PageResult<NotificationVO> listMy(Long userId, Integer isRead, int page, int pageSize);

    /**
     * 未读通知计数（驱动首页红点）
     */
    long countUnread(Long userId);

    /**
     * 单条标记已读（归属校验：通知不存在或非本人时静默成功，幂等且不暴露他人通知存在性）
     */
    void markRead(Long userId, Long notificationId);
}
