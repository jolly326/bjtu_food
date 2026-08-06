package com.bjtufood.notify.service;

import com.bjtufood.notify.entity.Notification;

/**
 * 消息通知服务接口
 */
public interface NotificationService {

    /**
     * 写入一条通知
     *
     * @param notification 通知实体
     */
    void notify(Notification notification);
}
