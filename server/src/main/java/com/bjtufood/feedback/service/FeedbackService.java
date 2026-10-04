package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackHandleReq;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.feedback.dto.ReportReasonVO;

import java.util.List;

/**
 * 用户反馈服务接口
 * 删除「我的反馈列表」listMy（前端 getMyFeedback 已删，反馈中心下线），保留 submit/listForAdmin/handle。
 */
public interface FeedbackService {

    /**
     * 提交意见反馈（学生/游客），status=pending。
     * <p>
     * 写入类型值域 = {@code FeedbackConst.WRITABLE_TYPES}（{@code bug} / {@code suggestion} / {@code other}）；
     * {@code content} 必填（≤1000 字）+ {@code images}（≤3 张）。
     * 文本过微信内容安全检测（msgSecCheck v2，scene=2）：risky 直接拒绝（400），pass/review 一律放行。
     *
     * @param userId 当前用户ID（游客为 null）
     * @param req    反馈内容
     */
    void submit(Long userId, FeedbackReq req);

    /**
     * 提交评价举报（{@code POST /reviews/{id}/report}），status=pending。
     * <p>
     * 结论以结构化原因（{@code reason}）为准（必选，值域 = {@code FeedbackConst.REPORT_REASON_VALUES}）；
     * 文本 {@code content} 可空（填写则过内容安检）；同用户对同一评价重复举报 → 400。
     * 落库为 {@code user_feedback} 的 {@code type='report'} 记录
     * （{@code sub=reason}、{@code related_type=review}、{@code related_id=reviewId}）。
     *
     * @param userId   当前用户ID（游客为 null，不做去重）
     * @param reviewId 被举报的评价ID（路径参数）
     * @param req      举报内容
     * @throws com.bjtufood.common.exception.BusinessException 原因缺失/非法、评价不存在（4001）、重复举报（400）
     */
    void report(Long userId, Long reviewId, ReportReq req);

    /**
     * 举报原因字典（公开只读，`GET /report-reasons`）。
     * <p>
     * **真源改为 `report_reason` 表**（A7 落地，2026-10-03）：原为代码常量 {@code FeedbackConst.REPORT_REASONS}，
     * 现由 {@code ReportReasonService#listEnabled()} 供给 —— 字典可维护、免发版、免客户端改动。
     * <p>
     * 出参结构**不变**（客户端契约零改动）：恰 {@code value} + {@code label}、无分页；
     * 变化只有两条 ——「**只下发启用项**」与「顺序来自表（拖拽后的 `order`）」。
     *
     * @return 举报原因字典项（value 机器值 + label 中文标签，按 `order` 升序，仅启用项）
     */
    List<ReportReasonVO> reportReasons();

    /**
     * 反馈列表（管理端，按状态/类型/用户过滤）
     * <p>
     * 内容安全态筛选入参已随 sec_state 全链退役删除。
     *
     * @param keyword 关键词（可选，对反馈内容 content 或管理员回复 reply 模糊匹配）
     */
    IPage<FeedbackAdminVO> listForAdmin(String category, String status, String type, Long userId, String keyword, int page, int pageSize);

    /**
     * 待办计数（运营看板）：按板块统计 status=pending 的总数。
     * <p>
     * 等价于 {@code listForAdmin(category, "pending", null, null, null, 1, 1).getTotal()}，
     * 但免去一次 LIMIT 1 列表查询（与 countWithoutDish / countHealth 口径一致）。
     *
     * @param category 板块（feedback / report）
     * @return 待处理条数
     */
    long countPending(String category);

    /**
     * 处理反馈：标记 handled + 写 reply/处理结论/handled_at
     * <p>
     * §7.10 决议：管理端「操作人身份」降级——单口令即单人，不再追究身份，
     * 故不再取当前管理员 ID 写 handler_id（列保留在库中，登记为 retired）。
     * <p>
     * §7.16：{@code reply} <b>必填</b>（trim 后非空白），落库并随回执通知发送，
     * 缺失/纯空白抛 400；Service 层为 Controller {@code @NotBlank} 的兜底，两者文案一致。
     * <p>
     * §7.23 第 5 条：支持处理结论——{@code outcome=handled}（通过/已处理，缺省）或
     * {@code outcome=rejected}（不采纳/退回）；结论为不采纳/退回时 {@code rejectReason} 必填
     * （1~200 字，纯空白视为未填写 → 400「请填写不采纳原因」），随回执一并向提交人展示。
     */
    void handle(Long id, FeedbackHandleReq req);

    /**
     * 账号归属迁移：把 fromUserId 的反馈改挂到 toUserId（仅改 {@code user_feedback.user_id}）。
     * <p>
     * 调用方 = {@code feedback.event.FeedbackOwnershipListener}（订阅 auth 域发布的
     * {@code UserOwnershipMigratedEvent}）。原先由 {@code AuthServiceImpl} 直接注入
     * FeedbackMapper 改写，属跨域写他域表。
     * <p>
     * 账号注销不迁移：注销只软删 user 行，反馈行与 user_id 保持不动，昵称由 join user 实时取。
     *
     * @param fromUserId 迁出账号ID
     * @param toUserId   迁入账号ID
     * @return 改挂条数（供日志）
     */
    int migrateOwnership(Long fromUserId, Long toUserId);
}
