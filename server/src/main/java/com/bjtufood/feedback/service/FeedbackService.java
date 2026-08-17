package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackMyVO;
import com.bjtufood.feedback.dto.FeedbackReq;

import java.util.List;

/**
 * 用户反馈服务接口
 */
public interface FeedbackService {

    /**
     * 提交反馈（学生），status=pending
     */
    void submit(Long userId, FeedbackReq req);

    /**
     * 我的反馈列表（学生，倒序：反馈中心查看反馈进度）
     */
    List<FeedbackMyVO> listMy(Long userId);

    /**
     * 反馈列表（管理端，按状态/类型/用户过滤）
     *
     * @param keyword 关键词（可选，对反馈内容 content 或管理员回复 reply 模糊匹配）
     */
    IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, String keyword, int page, int pageSize);

    /**
     * 处理反馈：标记 handled + 写 reply/handled_at/handler_id
     */
    void handle(Long id, Long handlerId, String reply);
}
