package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.feedback.dto.ReportAdminVO;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.feedback.entity.Report;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.service.ReportOptionService;
import com.bjtufood.feedback.service.ReportService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.service.ModerationTaskService;
import com.bjtufood.moderation.support.ModerationTaskFilters;
import com.bjtufood.review.dto.ReviewRelatedBriefVO;
import com.bjtufood.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评价举报服务实现（`report` 表 + 处置任务表）。
 * <p>
 * 提交链路：举报**无正文列**（结论以结构化原因为准）⇒ 无文本机审，
 * 故提交方法自身即事务边界（`report` 行 + 处置任务行同事务），无需另立落库 Bean。
 * 处置链路的副作用（联动隐藏评价）与归档同事务 —— 见 {@link #handle}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final UserService userService;
    /** 举报原因字典真源（`report_option` 表）：提交白名单 + 管理端中文名翻译 */
    private final ReportOptionService reportOptionService;
    /** 处置状态机（`moderation_task`）：提交预建、处置归档、列表批量回填 */
    private final ModerationTaskService moderationTaskService;
    /** 跨域只读契约：举报目标（评价）存在性与可见性校验 + 被举报内容摘要批量补齐 */
    private final ReviewService reviewService;

    /**
     * 提交举报。与反馈 / 纠错不同，本链路**没有外部 HTTP 调用**（无文本机审），
     * 故不需要「把事务边界收窄到落库一步」的处理，直接在方法上开事务即可。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, Long reviewId, ReportReq req) {
        // report.user_id 必填真实登录用户（外键 fk_report_user → user(id)）：无登录态即 401
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        // 举报目标必须存在且公开可见（已隐藏 / 已删除对外等价于不存在 → 4001）
        if (!reviewService.existsVisibleById(reviewId)) {
            throw new BusinessException(4001, "评价不存在");
        }
        // 举报原因值域 = `report_option` 表的**启用项**（停用的原因不能再被提交，
        // 但历史记录仍能翻译出中文）
        if (!reportOptionService.isSubmittable(req.getOptionId())) {
            throw new BusinessException(400, "举报原因非法");
        }
        // 去重：同一登录用户对同一评价的重复举报不新增
        DuplicateGuard.assertUnique(reportMapper, new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .eq(Report::getReviewId, reviewId), 400, "你已举报过该内容，我们会尽快处理，请勿重复提交");

        Report report = new Report();
        report.setUserId(userId);
        report.setReviewId(reviewId);
        report.setOptionId(req.getOptionId());
        reportMapper.insert(report);
        // 同一事务内预建处置任务行：任一步失败一并回滚（见 ModerationTaskService#create）
        moderationTaskService.create(ModerationConst.TYPE_REPORT, report.getId());
    }

    @Override
    public IPage<ReportAdminVO> listForAdmin(String status, Long userId, int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        String normalizedStatus = ParamValidator.optionalInWhitelist(status, ModerationConst.STATUSES, "处理状态");
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .eq(userId != null, Report::getUserId, userId);
        if (StringUtils.hasText(normalizedStatus)) {
            ModerationTaskFilters.byStatus(wrapper, ModerationConst.TYPE_REPORT, "report.id", normalizedStatus);
        }
        wrapper.orderByDesc(Report::getCreatedAt);
        IPage<Report> p = reportMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Long> userIds = p.getRecords().stream()
                .map(Report::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        // 昵称投影经 auth 域只读契约下发（P-0-1：本域不注入 UserMapper）
        Map<Long, String> userMap = userService.mapNameByIds(userIds);
        // 原因中文名（字典**全量含停用项**：历史举报仍要能翻译出中文；逐页取一次）
        Map<Long, String> optionNames = reportOptionService.nameByIdAll();
        // 被举报评价摘要：只收集 review_id 非空的行后**批量**取（空集合不发查询）
        Set<Long> reviewIds = new HashSet<>();
        for (Report r : p.getRecords()) {
            if (r.getReviewId() != null) {
                reviewIds.add(r.getReviewId());
            }
        }
        Map<Long, ReviewRelatedBriefVO> reviewBriefs = reviewService.mapRelatedBriefByIds(reviewIds);
        // 处置结果（处置文案真源在任务表）：按本页 ID 集合批量回填
        Set<Long> ids = new HashSet<>();
        p.getRecords().forEach(r -> ids.add(r.getId()));
        Map<Long, ModerationTaskVO> tasks = moderationTaskService.mapByTypeAndIds(ModerationConst.TYPE_REPORT, ids);

        return PageUtil.toVoPage(p,
                recs -> recs.stream().map(r -> toAdminVO(r, userMap, optionNames, reviewBriefs, tasks)).toList());
    }

    /** 管理端 VO 转换：补齐举报人昵称、原因中文名、被举报评价摘要与处置结果 */
    private ReportAdminVO toAdminVO(Report r, Map<Long, String> userMap, Map<Long, String> optionNames,
                                    Map<Long, ReviewRelatedBriefVO> reviewBriefs,
                                    Map<Long, ModerationTaskVO> tasks) {
        ReportAdminVO vo = new ReportAdminVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setUserName(userMap.get(r.getUserId()));
        vo.setOptionId(r.getOptionId());
        // 字典缺失（原因已被物理删除的极端情况）时回退 null：由前端按「—」呈现
        vo.setOptionName(r.getOptionId() == null ? null : optionNames.get(r.getOptionId()));
        vo.setReviewId(r.getReviewId());
        // 🔴 被举报评价已删除：行保留、review_id 置 NULL（外键 ON DELETE SET NULL）——
        // 摘要与可见性一并置空：不是在说「评价还在但读不到」
        boolean reviewDeleted = r.getReviewId() == null;
        vo.setReviewDeleted(reviewDeleted);
        if (!reviewDeleted) {
            ReviewRelatedBriefVO brief = reviewBriefs.get(r.getReviewId());
            if (brief != null) {
                vo.setReviewContent(brief.getContent());
                vo.setReviewDishName(brief.getDishName());
                vo.setReviewHidden(brief.isHidden());
            }
        }
        vo.setCreatedAt(r.getCreatedAt());
        ModerationTaskVO task = tasks.get(r.getId());
        if (task != null) {
            vo.setStatus(task.getStatus());
            vo.setOutcome(task.getOutcome());
            vo.setReplyTitle(task.getReplyTitle());
            vo.setReplyContent(task.getReplyContent());
            vo.setHandledByName(task.getHandledByName());
            vo.setHandledAt(task.getHandledAt());
        }
        return vo;
    }

    /**
     * 处置举报：更新处置任务行 + 投递回执 +（可选）联动隐藏被举报评价，**同一事务**。
     * <p>
     * <b>为何先查「仍待处置」再隐藏</b>：隐藏是<b>有副作用且不可逆感知</b>的动作，
     * 而 {@code ModerationTaskService#handle} 会对已处置记录报 400 —— 若不先判，
     * 「已处置后再点一次」会先隐藏评价、再报错，留下未归档的副作用。
     * 真正的防重复结论仍由 {@code handle} 在事务内强制（本处只是前置短路）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, ModerationHandleReq req, Long adminId) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(4001, "举报不存在");
        }
        String outcome = resolveOutcome(req.getOutcome());
        boolean rejected = ModerationConst.OUTCOME_REJECTED.equals(outcome);

        // 联动隐藏（B3）：仅「通过 + 勾选 + 有被举报评价 + 记录仍待处置」时执行；
        // 幂等：评价已隐藏 / 已删除 ⇒ hideIfVisible 返回 false，不重复投递回执
        ModerationTaskVO current = moderationTaskService.findByTypeAndId(ModerationConst.TYPE_REPORT, id);
        boolean stillPending = current != null && ModerationConst.STATUS_PENDING.equals(current.getStatus());
        boolean hiddenThisTime = false;
        if (Boolean.TRUE.equals(req.getHideReview()) && !rejected && report.getReviewId() != null && stillPending) {
            hiddenThisTime = reviewService.hideIfVisible(report.getReviewId());
        }

        moderationTaskService.handle(ModerationConst.TYPE_REPORT, id, outcome, req.getReplyTitle(),
                composeReplyContent(req.getReplyContent(), hiddenThisTime), adminId, report.getUserId());
    }

    /** 处置结论归一：null / 空白按 handled 缺省；白名单外一律 400（不静默降级） */
    private static String resolveOutcome(String outcome) {
        if (!StringUtils.hasText(outcome)) {
            return ModerationConst.OUTCOME_HANDLED;
        }
        return ParamValidator.requiredInWhitelist(outcome, ModerationConst.OUTCOMES, "处理结论");
    }

    /**
     * 处置文案组装：受理且顺带隐藏了评价时**必须说明** ——
     * 用户看不到那条评价了，不说明即为「凭空消失」。
     *
     * @param rawContent 管理员填写处置文案（可空 → 由缺省文案兜底）
     * @return 组装后的处置文案；未隐藏且未填时为 null（交给缺省文案）
     */
    private static String composeReplyContent(String rawContent, boolean hiddenThisTime) {
        String base = rawContent == null ? null : rawContent.trim();
        if (!StringUtils.hasText(base)) {
            base = null;
        }
        if (!hiddenThisTime) {
            return base;
        }
        return base == null ? "已隐藏该评价" : base + "（已隐藏该评价）";
    }
}
