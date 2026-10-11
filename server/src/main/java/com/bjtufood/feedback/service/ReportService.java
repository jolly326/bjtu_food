package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.dto.ReportAdminVO;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.moderation.dto.ModerationHandleReq;

/**
 * 评价举报服务接口（`report` 表）。
 * <p>
 * 举报是**独立资源**：提交走 {@code POST /reviews/{id}/report}，管理端走 {@code /admin/reports}，
 * 与意见反馈（{@code /feedback} + {@code /admin/feedbacks}）各自成链。
 */
public interface ReportService {

    /**
     * 提交评价举报（🔑 需登录、免邮箱认证）。
     * <p>
     * 写入 `report` + 在**同一事务**内预建处置任务行（`status=pending`）。
     * 校验：被举报评价须存在且公开可见（否则 4001）；{@code optionId} 必填且命中
     * `report_option` 启用项（否则 400）；同一用户对同一评价重复举报 → 400。
     * <p>
     * 举报无正文列（结论以结构化原因为准）⇒ 无文本送检；但举报是「内容治理入口」，
     * 目标评价的存在性 / 可见性由 review 域只读契约实时判定。
     *
     * @param userId   举报人（须为真实登录用户，去重按该维度）
     * @param reviewId 被举报的评价ID（路径参数）
     * @param req      举报原因
     * @throws com.bjtufood.common.exception.BusinessException 原因缺失/非法（400）、评价不存在（4001）、重复举报（400）
     */
    void submit(Long userId, Long reviewId, ReportReq req);

    /**
     * 管理端举报列表（`GET /admin/reports`）。
     * <p>
     * 筛选按「处置任务状态」（`moderation_task.status`），排序固定 `report.created_at DESC`；
     * 出参补齐举报人昵称、原因中文名（含停用项）、被举报评价摘要与处置结果。
     * {@code review_id IS NULL} ⇒ 下发「被举报评价已删除」标记（评价摘要与可见性置空）。
     *
     * @param status 处置状态：pending/handled（可空 = 全部；非法值 400）
     * @param userId 按举报人筛选（可空）
     */
    IPage<ReportAdminVO> listForAdmin(String status, Long userId, int page, int pageSize);

    /**
     * 处置举报（`PUT /admin/reports/{id}`）：更新处置任务行 + 投递站内回执（同一事务）。
     * <p>
     * {@code outcome=handled} 且 {@code hideReview=true} 时**联动隐藏被举报评价**
     * （与 {@code PUT /admin/reviews/{id}/hidden} 同一动作；评价已隐藏 / 已删除时静默成功）。
     * {@code outcome=rejected} 不消费 {@code hideReview}，且处置文案必填（即为不采纳原因）。
     *
     * @param id      举报ID
     * @param req     处置请求（{@code replyTitle} / {@code replyContent} / {@code outcome} / {@code hideReview}）
     * @param adminId 处置人账号ID（SecurityUtil 取，不信任请求体）
     * @throws com.bjtufood.common.exception.BusinessException code=4001 举报不存在
     */
    void handle(Long id, ModerationHandleReq req, Long adminId);
}
