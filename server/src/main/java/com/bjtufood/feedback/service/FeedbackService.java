package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.constant.FeedbackConst;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackHandleReq;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.feedback.dto.ReportReasonVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户反馈服务接口
 * 2026-09-07：删除「我的反馈列表」listMy（前端 getMyFeedback 已删，反馈中心下线），保留 submit/listForAdmin/handle。
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
     * 举报原因字典（<b>两端各自暴露</b>，数据同源）。
     * <p>
     * <b>为何两个端点而不是合并为一个</b>：小程序走 {@code /api/v1/**} + JWT，
     * 管理后台走 {@code /api/v1/admin/**} + {@code X-Admin-Token}，<b>鉴权体系互不通</b>。
     * 2026-09-29 审计发现 web 曾直接调用学生端 {@code GET /feedback/report-reasons}
     * （属「一个接口两端调用」）。该端点在学生端白名单内是 {@code permitAll} 故当时能跑，
     * 但一旦学生端接口纳入 JWT 鉴权，管理后台会立刻 401 失效。
     * 故管理端另开 {@code GET /admin/feedbacks/report-reasons}，两端彻底解耦。
     * <p>
     * <b>数据仍然同源</b>：两端出参均由 {@code FeedbackConst.REPORT_REASONS} 构造，
     * 构造逻辑下沉到 {@link #reportReasons()} 供两个 Controller 复用，
     * 避免「复制两份常量遍历」导致日后口径漂移（曾在前端发生过同类问题）。
     *
     * @return 举报原因字典项（value 机器值 + label 中文标签，按声明序）
     */
    default List<ReportReasonVO> reportReasons() {
        List<FeedbackConst.ReportReason> reasons = FeedbackConst.REPORT_REASONS;
        List<ReportReasonVO> result = new ArrayList<>(reasons.size());
        for (FeedbackConst.ReportReason reason : reasons) {
            result.add(new ReportReasonVO(reason.value(), reason.label()));
        }
        return result;
    }

    /**
     * 反馈列表（管理端，按状态/类型/用户过滤）
     * <p>
     * 内容安全态筛选入参已随 sec_state 全链退役删除（2026-09-15 取消人工复核，无复核队列）。
     *
     * @param keyword 关键词（可选，对反馈内容 content 或管理员回复 reply 模糊匹配）
     */
    IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, String keyword, int page, int pageSize);

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
