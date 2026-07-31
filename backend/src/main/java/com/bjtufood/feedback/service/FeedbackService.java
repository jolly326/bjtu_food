package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackReq;

/**
 * 用户反馈服务接口
 */
public interface FeedbackService {

    /**
     * 提交反馈（学生），status=pending
     */
    void submit(Long userId, FeedbackReq req);

    /**
     * 反馈列表（管理端，按状态/类型过滤）
     */
    IPage<FeedbackAdminVO> listForAdmin(String status, String type, int page, int pageSize);

    /**
     * 处理反馈：标记 handled + 写 reply/handled_at/handler_id
     */
    void handle(Long id, Long handlerId, String reply);
}
