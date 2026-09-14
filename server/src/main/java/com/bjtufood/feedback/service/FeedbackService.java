package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackHandleReq;
import com.bjtufood.feedback.dto.FeedbackReq;

/**
 * 用户反馈服务接口
 * 2026-09-07：删除「我的反馈列表」listMy（前端 getMyFeedback 已删，反馈中心下线），保留 submit/listForAdmin/handle。
 */
public interface FeedbackService {

    /**
     * 提交反馈（学生），status=pending。
     * 文本过微信内容安全检测（msgSecCheck v2，scene=2）；images 入库、sec_state 按机检结果落库。
     */
    void submit(Long userId, FeedbackReq req);

    /**
     * 反馈列表（管理端，按状态/类型/内容安全状态/用户过滤）
     *
     * @param secState 内容安全状态筛选（可选，pass/review/rejected，管理端复核队列用）
     * @param keyword  关键词（可选，对反馈内容 content 或管理员回复 reply 模糊匹配）
     */
    IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, String secState, String keyword, int page, int pageSize);

    /**
     * 处理反馈：标记 handled + 写 reply/处理结论/handled_at
     * <p>
     * §7.10 决议：管理端「操作人身份」降级——单口令即单人，不再追究身份，
     * 故不再取当前管理员 ID 写 handler_id（列保留在库中，登记为 retired）。
     * <p>
     * §7.16（2026-09-14）：{@code reply} <b>必填</b>（trim 后非空白），落库并随回执通知发送，
     * 缺失/纯空白抛 400；Service 层为 Controller {@code @NotBlank} 的兜底，两者文案一致。
     * <p>
     * §7.23 第 5 条（2026-09-15）：支持处理结论——{@code outcome=handled}（通过/已处理，缺省）或
     * {@code outcome=rejected}（不采纳/退回）；结论为不采纳/退回时 {@code rejectReason} 必填
     * （1~200 字，纯空白视为未填写 → 400「请填写不采纳原因」），随回执一并向提交人展示。
     */
    void handle(Long id, FeedbackHandleReq req);
}
