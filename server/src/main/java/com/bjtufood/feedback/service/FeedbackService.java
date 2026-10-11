package com.bjtufood.feedback.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.moderation.dto.ModerationHandleReq;

/**
 * 意见反馈服务接口。
 * <p>
 * 职责：提交 / 管理端列表 / 处置归档（处置状态机在 {@code ModerationTaskService}）/
 * 账号归属迁移。举报与字典各有自己的服务（{@link ReportService} / {@link ReportOptionService}）。
 */
public interface FeedbackService {

    /**
     * 提交意见反馈（🔑 需登录）。
     * <p>
     * 写入 `feedback` + 在**同一事务**内预建处置任务行（`status=pending`）。
     * 校验：{@code optionId} 必填且命中 `feedback_option` 启用项；{@code content} 非空（≤1000 字）；
     * 文本过本地词库 + 微信内容安全检测（msgSecCheck v2，scene=2）：risky 直接拒绝（400），
     * pass / review 一律放行；{@code images} ≤3 张且逐项 COS 白名单校验。
     *
     * @param userId 当前用户ID（须为真实登录用户；无登录态即 401）
     * @param req    反馈内容
     */
    void submit(Long userId, FeedbackReq req);

    /**
     * 管理端反馈列表（`GET /admin/feedbacks`）。
     * <p>
     * 筛选按「处置任务状态」（`moderation_task.status`，提交时预建 ⇒ 恒有行），
     * 排序固定 `feedback.created_at DESC`；关键词匹配反馈正文**或**处置文案。
     * 出参补齐提交人昵称、类型中文名（含停用项）与处置结果（含处理人登录名）。
     *
     * @param status   处置状态：pending/handled（可空 = 全部；非法值 400）
     * @param optionId 按反馈类型筛选（可空；从字典视角看反馈）
     * @param userId   按提交人筛选（可空）
     * @param keyword  关键词（可空；匹配反馈正文或处置文案）
     */
    IPage<FeedbackAdminVO> listForAdmin(String status, Long optionId, Long userId, String keyword,
                                        int page, int pageSize);

    /**
     * 处置反馈（`PUT /admin/feedbacks/{id}`）：更新处置任务行 + 投递站内回执（同一事务）。
     * <p>
     * 处置文案 / 结论的校验与缺省兜底、已处置再处置 → 400、任务行缺失 → 4001 均在
     * {@code ModerationTaskService#handle} 内统一强制。
     *
     * @param id      反馈ID
     * @param req     处置请求（{@code replyTitle} / {@code replyContent} / {@code outcome}）
     * @param adminId 处置人账号ID（SecurityUtil 取，不信任请求体）
     * @throws com.bjtufood.common.exception.BusinessException code=4001 反馈不存在
     */
    void handle(Long id, ModerationHandleReq req, Long adminId);

    /**
     * 账号归属迁移：把 fromUserId 的**本域用户提交**改挂到 toUserId
     * （`feedback.user_id` 与 `report.user_id`，同一事务）。
     * <p>
     * 调用方 = {@code feedback.event.FeedbackOwnershipListener}（订阅 auth 域发布的
     * {@code UserOwnershipMigratedEvent}）—— auth 域**不直接改写**本域表。
     * <p>
     * 账号注销不迁移：注销只软删 user 行，提交行与 user_id 保持不动，昵称由 join user 实时取。
     *
     * @param fromUserId 迁出账号ID
     * @param toUserId   迁入账号ID
     * @return 改挂条数（供日志；两张表合计）
     */
    int migrateOwnership(Long fromUserId, Long toUserId);
}
