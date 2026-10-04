package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.feedback.constant.FeedbackConst;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.common.utils.UgcImageValidator;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackHandleReq;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.dto.ReportReasonVO;
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.review.dto.ReviewRelatedBriefVO;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.service.FeedbackService;
import com.bjtufood.feedback.service.ReportReasonService;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈服务实现
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    /**
     * 落库事务边界：写路径的 {@code @Transactional} 只包住**落库**本身。
     * 原先事务从方法入口就开始、横跨微信机审的 HTTP 外呼（超时 5s）⇒ 期间一直占用数据库连接；
     * HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。现改为「先机审（无事务）→ 再落库（开事务）」。
     * 必须是**独立 Bean**：Spring 事务靠代理生效，同类的自调用不会开启事务。
     */
    private final FeedbackPersister feedbackPersister;
    /** 跨域只读契约：管理端「提交人」昵称投影 + 内容安检取 openid（P0-1，替代 UserMapper 直连） */
    private final UserService userService;
    /** 跨域只读契约：管理端列表补全「关联菜品名」用（DEV-04）；仅按 id 批量取 name，不参与反馈写入。 */
    private final DishService dishService;
    private final LocalSensitiveFilter localSensitiveFilter;
    private final NotificationService notificationService;
    /** 举报原因字典真源（report_reason 表，A7 落地后替代原常量） */
    private final ReportReasonService reportReasonService;
    private final ContentSecurityService contentSecurityService;
    private final ImageUrlUtil imageUrlUtil;
    /** 跨域只读契约：举报目标（评价）存在性与可见性校验（方案 B 举报子资源） */
    private final ReviewService reviewService;

    /**
     * 提交反馈。<b>本方法刻意不加 {@code @Transactional}</b>：事务边界收窄到落库一步
     * （{@link FeedbackPersister#insert}），使微信机审的外呼期间不占用数据库连接。
     */
    @Override
    public void submit(Long userId, FeedbackReq req) {
        // 类型写入白名单（方案 B ）：仅纯反馈三类可写（bug / suggestion / other），
        // 非法 / 历史遗留（issue / add / error / report）→ 400（不再原样落库）；
        // 举报已迁出为 POST /reviews/{id}/report，纠错早前迁出为 POST /dishes/{id}/correction。
        String type = ParamValidator.requiredInWhitelist(req.getType(), FeedbackConst.WRITABLE_TYPES, "反馈类型");
        if (!StringUtils.hasText(req.getContent())) {
            throw new BusinessException(400, "反馈内容不能为空");
        }
        String content = localSensitiveFilter.filter(req.getContent());

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        // 纯反馈无二级分类 / 无关联对象（历史列保持 NULL）
        feedback.setSub(null);
        feedback.setContent(content);
        feedback.setRelatedType(null);
        feedback.setRelatedId(null);
        feedback.setStatus(FeedbackConst.STATUS_PENDING);

        // ---- 内容安全检测（全部 UGC 过微信内容安全检测）----
        // 文本 msgSecCheck v2（scene=2）；risky 由 checkText 统一拦截（400）；
        // 边界：游客（userId=null）与无 openid 账号跳过机审放行（本地词库兜底）。
        checkUgcText(userId, feedback.getContent());
        feedback.setImages(UgcImageValidator.encode(req.getImages(), "反馈", imageUrlUtil));
        feedbackPersister.insert(feedback);
    }

    /**
     * 举报评价。<b>本方法刻意不加 {@code @Transactional}</b>：与 {@link #submit} 同理，
     * 存在性/去重/机审均在无事务状态下完成，仅落库一步开事务。
     */
    @Override
    public List<ReportReasonVO> reportReasons() {
        // 字典真源 = report_reason 表（A7）；出参结构不变（恰 value + label、仅启用项、按 order 升序）
        return reportReasonService.listEnabled();
    }

    @Override
    public void report(Long userId, Long reviewId, ReportReq req) {
        // 举报目标必须存在且公开可见（已隐藏 / 已删除对外等价于不存在 → 4001）
        if (!reviewService.existsVisibleById(reviewId)) {
            throw new BusinessException(4001, "评价不存在");
        }
        // 举报原因必选，值域 = **report_reason 表**（存在且启用；停用的原因不能再被提交，
        // 但历史记录仍能翻译出中文 —— 见 docs/web/A-主数据维护/A7-举报原因管理.md）
        String reason = req.getReason() == null ? null : req.getReason().trim();
        if (!reportReasonService.isSubmittable(reason)) {
            throw new BusinessException("举报原因非法");
        }
        // 去重（§7.11 第 3 条）：同一登录用户对同一评价的重复举报不再新增（游客 userId=null 无身份标识，不去重）
        if (userId != null) {
            DuplicateGuard.assertUnique(feedbackMapper, new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getUserId, userId)
                    .eq(Feedback::getType, FeedbackConst.TYPE_REPORT)
                    .eq(Feedback::getRelatedType, FeedbackConst.RELATED_REVIEW)
                    .eq(Feedback::getRelatedId, reviewId), 400, "你已举报过该内容，我们会尽快处理，请勿重复提交");
        }
        // 补充文本可空；填写则过本地词库 + 微信机审（空文本跳过送检省额度）
        String content = StringUtils.hasText(req.getContent()) ? localSensitiveFilter.filter(req.getContent()) : "";
        if (StringUtils.hasText(content)) {
            checkUgcText(userId, content);
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(FeedbackConst.TYPE_REPORT);
        feedback.setSub(reason);
        feedback.setContent(content);
        feedback.setRelatedType(FeedbackConst.RELATED_REVIEW);
        feedback.setRelatedId(reviewId);
        feedback.setStatus(FeedbackConst.STATUS_PENDING);
        feedback.setImages(UgcImageValidator.encode(req.getImages(), "举报", imageUrlUtil));
        feedbackPersister.insert(feedback);
    }

    /**
     * 文本内容安全检测：登录用户取 openid 调 msgSecCheck v2（仅拦截，不落库安全态）。
     * <p>
     * 结果语义：risky 由 {@code checkText} 抛 400 拦截；
     * pass 与内容安全检测 review 均视为放行（sec_state 已全链退役，无待复核落库值）。
     * 边界：游客（userId=null）与无 openid 账号无 openid 可用，内容安全检测内部按既有口径跳过放行。
     */
    private void checkUgcText(Long userId, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        // 只取 openid 判定要素（P0-1：feedback 不再 import auth 实体/Mapper；
        // 用户不存在 ⇔ getAuthContext 返回 null ⇔ openid 为空，与原实现同效）
        UserAuthContextVO user = userService.getAuthContext(userId);
        String openid = user == null ? null : user.getOpenid();
        contentSecurityService.checkText(openid, content, 2);
    }

    @Override
    public IPage<FeedbackAdminVO> listForAdmin(String category, String status, String type, Long userId, String keyword, int page, int pageSize) {
        int[] norm = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];

        LambdaQueryWrapper<Feedback> wrapper = buildAdminQuery(category, status, type, userId, keyword);

        IPage<Feedback> p = feedbackMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Long> userIds = p.getRecords().stream()
                .map(Feedback::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        // 昵称投影经 auth 域只读契约下发（P0-1：不再注入 UserMapper；空集合返回空 Map，不发起查询）
        Map<Long, String> userMap = userService.mapNicknameByIds(userIds);

        // 关联菜品名（DEV-04）：一次 IN 查询取回本页全部 dish 关联 id → name（消除 N+1）。
        // 口径：不过滤 status/上架态——信息纠错的对象可能已被下架，管理端仍需看到菜品名回看纠错内容；
        //       菜品已物理删除时不在结果集，VO 保持 null（前端按「菜品已删除」缺省展示）。
        Map<Long, String> dishNameMap = batchRelatedDishNames(p.getRecords());
        // B3：举报原因中文名（`report_reason` 字典；量级 ≤8 条，逐页取一次即可，反馈行不消费）
        Map<String, String> reasonLabelMap = new java.util.HashMap<>();
        for (ReportReasonVO reason : reportReasonService.listEnabled()) {
            reasonLabelMap.put(reason.getValue(), reason.getLabel());
        }
        // B3：被举报评价摘要 —— **只收集 report 行的 relatedId 后批量取**（反馈行不参与，避免白取）
        java.util.Set<Long> reportReviewIds = new java.util.HashSet<>();
        for (Feedback f : p.getRecords()) {
            if (FeedbackConst.TYPE_REPORT.equals(f.getType()) && f.getRelatedId() != null) {
                reportReviewIds.add(f.getRelatedId());
            }
        }
        java.util.Map<Long, ReviewRelatedBriefVO> reviewBriefs =
                reviewService.mapRelatedBriefByIds(reportReviewIds);

        return com.bjtufood.common.utils.PageUtil.toVoPage(p,
                recs -> recs.stream()
                        .map(f -> toAdminVO(f, userMap, dishNameMap, reasonLabelMap, reviewBriefs))
                        .toList());
    }

    /** 抽取管理端列表 / 计数的公共查询构造（含入参白名单校验与板块分流），供 listForAdmin 与 countPending 复用。 */
    private LambdaQueryWrapper<Feedback> buildAdminQuery(String category, String status, String type, Long userId, String keyword) {
        status = ParamValidator.optionalInWhitelist(status, FeedbackConst.QUERY_STATUSES, "处理状态");
        type = ParamValidator.optionalInWhitelist(type, FeedbackConst.QUERY_TYPES, "反馈类型");
        category = ParamValidator.optionalInWhitelist(category, FeedbackConst.QUERY_CATEGORIES, "板块");
        boolean reportOnly = "report".equals(category);
        boolean feedbackOnly = "feedback".equals(category);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(StringUtils.hasText(status), Feedback::getStatus, status)
                .eq(StringUtils.hasText(type), Feedback::getType, type)
                .eq(reportOnly, Feedback::getType, FeedbackConst.TYPE_REPORT)
                .ne(feedbackOnly, Feedback::getType, FeedbackConst.TYPE_REPORT)
                .eq(userId != null, Feedback::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Feedback::getContent, kw).or().like(Feedback::getReply, kw));
        }
        wrapper.orderByDesc(Feedback::getCreatedAt);
        return wrapper;
    }

    @Override
    public long countPending(String category) {
        return feedbackMapper.selectCount(buildAdminQuery(category, "pending", null, null, null));
    }

    /**
     * 批量查询本页反馈关联的菜品名（DEV-04）：{@code relatedType='dish'} 且 relatedId 非空的 id 去重后
     * 一次 IN 查询，返回 dishId → dish.name 映射（空集合返回空 Map，不发起查询）。
     */
    private Map<Long, String> batchRelatedDishNames(List<Feedback> records) {
        List<Long> dishIds = records.stream()
                .filter(f -> FeedbackConst.RELATED_DISH.equals(f.getRelatedType()))
                .map(Feedback::getRelatedId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        // 菜品名经 dish 域只读契约下发（P0-1：不再注入 DishMapper；口径=不过滤上架态，见接口注释）
        return dishService.mapNameByIds(dishIds);
    }

    /**
     * 管理端 VO 转换：补齐昵称、配图（JSON→数组）、关联菜品名（DEV-04）；
     * **举报行**另补原因中文名与被举报评价 ID（B3）；内容安全态已随 sec_state 退役。
     */
    private FeedbackAdminVO toAdminVO(Feedback f, Map<Long, String> userMap, Map<Long, String> dishNameMap,
                                      Map<String, String> reasonLabelMap,
                                      java.util.Map<Long, ReviewRelatedBriefVO> reviewBriefs) {
        FeedbackAdminVO vo = new FeedbackAdminVO();
        vo.setId(f.getId());
        vo.setUserId(f.getUserId());
        vo.setUserNickname(userMap.get(f.getUserId()));
        vo.setType(f.getType());
        // 二级分类：仅 suggestion 有效（老数据/其他类型该列为 NULL，前端按「未分类」展示）
        vo.setSub(f.getSub());
        vo.setContent(f.getContent());
        List<String> images = JsonListUtil.parseStringList(f.getImages());
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        // contact 已随列退役，管理端 VO 不再返回联系方式
        vo.setRelatedType(f.getRelatedType());
        vo.setRelatedId(f.getRelatedId());
        // 关联菜品名（DEV-04）：仅 relatedType=dish 且 relatedId 非空时按映射填充（含已下架菜品）；
        // 其他关联类型/无关联/菜品已物理删除 → null（前端按「无」缺省展示）。
        // 注：映射在无 dish 关联时为空不可变 Map（Map.of()），故必须先判 relatedId 非空再取值。
        Long relatedId = f.getRelatedId();
        vo.setRelatedDishName(relatedId != null && FeedbackConst.RELATED_DISH.equals(f.getRelatedType())
                ? dishNameMap.get(relatedId)
                : null);
        vo.setStatus(f.getStatus());
        // 处理结论回显（§7.23 第 5 条）：表无 outcome 物理列，按 status + reject_reason 派生——
        // handle() 落库保证「rejected ⇒ reject_reason 非空、handled ⇒ reject_reason 为 NULL」，
        // 故 handled 且 rejectReason 非空即 rejected，否则 handled（历史存量 reject_reason=NULL → handled，
        // 与缺省「已处理」一致）；pending（未处理）保持 null。
        vo.setOutcome(FeedbackConst.STATUS_HANDLED.equals(f.getStatus())
                ? (StringUtils.hasText(f.getRejectReason())
                        ? FeedbackConst.OUTCOME_REJECTED
                        : FeedbackConst.OUTCOME_HANDLED)
                : null);
        vo.setReply(f.getReply());
        vo.setRejectReason(f.getRejectReason());
        vo.setCreatedAt(f.getCreatedAt());
        vo.setHandledAt(f.getHandledAt());
        // B2：列表统一带 updatedAt（表已有列，无需 DDL）
        vo.setUpdatedAt(f.getUpdatedAt());
        // B3 举报私有字段：仅 type=report 填充（反馈行保持 null，避免端上误读成「有举报原因」）
        if (FeedbackConst.TYPE_REPORT.equals(f.getType())) {
            vo.setReason(f.getSub());
            // 字典缺失（历史机器值已停用）时回退机器值本身：宁可显示原始值，也不要空白
            vo.setReasonLabel(reasonLabelMap.getOrDefault(f.getSub(), f.getSub()));
            vo.setReviewId(f.getRelatedId());
            // 内嵌「被举报内容」摘要：管理员不跳页即可看到被举报了什么，并据 hidden 决定
            // 「同时隐藏」复选是否置灰（评价已隐藏时置灰，避免重复处置）
            ReviewRelatedBriefVO brief = reviewBriefs.get(f.getRelatedId());
            if (brief != null) {
                vo.setReviewContent(brief.getContent());
                vo.setReviewDishName(brief.getDishName());
                vo.setReviewHidden(brief.isHidden());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, FeedbackHandleReq req) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        // 已处理（status != 'pending'）再处理 → 400：防处理结论被静默改写 + 回执重复投递（口径见 B2/B3 错误码）
        if (!FeedbackConst.STATUS_PENDING.equals(feedback.getStatus())) {
            throw new BusinessException("该记录已处理");
        }
        // B2（2026-10-03）：回复**可选** —— 仅 rejected 结论要求 rejectReason；handled 允许无回复
        // （回执退化为固定文案）。此处只卡长度，不再拦必填；空值一律归一为 null 落库。
        String trimmedReply = req.getReply() == null ? null : req.getReply().trim();
        if (trimmedReply != null && trimmedReply.isEmpty()) {
            trimmedReply = null;
        }
        // 上限 600 字（回执正文 = 前缀 + 回复全文，须 ≤ notification.content 的 1024 列宽）
        if (trimmedReply != null && trimmedReply.length() > FeedbackConst.REPLY_MAX_LENGTH) {
            throw new BusinessException("处理回复不能超过" + FeedbackConst.REPLY_MAX_LENGTH + "字");
        }
        // §7.23 第 5 条：处理结论——handled=通过/已处理（缺省）；rejected=不采纳/退回。
        // 白名单外一律 400（PR-06），不再静默降级；rejectReason 仅在 rejected 结论下消费与落库。
        String outcome = req.getOutcome() == null || req.getOutcome().isBlank()
                ? FeedbackConst.OUTCOME_HANDLED
                : req.getOutcome().trim();
        if (!FeedbackConst.OUTCOMES.contains(outcome)) {
            throw new BusinessException("处理结论非法（仅允许 handled=通过/已处理、rejected=不采纳/退回）");
        }
        boolean rejected = FeedbackConst.OUTCOME_REJECTED.equals(outcome);
        String rejectReason = null;
        if (rejected) {
            // 不采纳/退回 → reject_reason 必填：1~200 字，纯空白视为未填写 → 400
            rejectReason = req.getRejectReason() == null ? null : req.getRejectReason().trim();
            if (!StringUtils.hasText(rejectReason)) {
                throw new BusinessException("请填写不采纳原因");
            }
            if (rejectReason.length() > FeedbackConst.REJECT_REASON_MAX_LENGTH) {
                throw new BusinessException("不采纳原因不能超过" + FeedbackConst.REJECT_REASON_MAX_LENGTH + "字");
            }
        }
        // B3：处置举报时**联动隐藏被举报评价**（此前与管理端「隐藏评价」完全独立，管理员极易漏处置）。
        // 仅在「是举报 + 有被举报评价 + 该评价当前未隐藏」时执行；隐藏本身会向作者投递回执（B1 口径）。
        boolean hiddenThisTime = false;
        if (Boolean.TRUE.equals(req.getHideReview()) && !rejected
                && FeedbackConst.TYPE_REPORT.equals(feedback.getType())
                && feedback.getRelatedId() != null) {
            hiddenThisTime = reviewService.hideIfVisible(feedback.getRelatedId());
        }
        feedback.setStatus(FeedbackConst.STATUS_HANDLED);
        feedback.setReply(trimmedReply);
        feedback.setRejectReason(rejectReason);
        feedback.setHandledAt(LocalDateTime.now());
        // §7.10：管理端操作人身份降级（单口令即单人），handler_id 一直未写；
        // 该列已于零消费退役删除（schema.sql drop_zero_consumer_columns），无需再处理。
        feedbackMapper.updateById(feedback);
        // 处理结果回执（携带处理结论与不采纳原因）：向「可归属」提交人（提交时带 userId 的登录态，含游客）投递
        sendFeedbackReceipt(feedback, rejected, trimmedReply, rejectReason, hiddenThisTime);
    }

    /**
     * 反馈处理结果回执（§7.23 第 5 条：回执携带处理结论；不采纳/退回时一并展示不采纳原因）。
     * <p>
     * 归属判据：提交时带 userId（登录态）即投递 —— **不按邮箱认证过滤**（消息中心为登录级能力，
     * 游客提交的反馈同样保留可回执身份）。
     * 投递失败不影响处理结果（独立 try 分支，异常不外抛到主流程）。
     *
     * @param rejected      true=处理结论为不采纳/退回（此时 rejectReason 非空，handle 已校验）
     * @param reply         处理回复（**可为 null**；为空时回执正文退化为固定结论文案）
     * @param rejectReason  不采纳原因（rejected=true 时非空；否则为 null，不参与文案）
     * @param hiddenThisTime 本次处置**顺带隐藏**了被举报评价（B3）—— 回执据此处置说明"已隐藏该评价"
     */
    private void sendFeedbackReceipt(Feedback feedback, boolean rejected, String reply, String rejectReason,
                                     boolean hiddenThisTime) {
        Long userId = feedback.getUserId();
        if (userId == null) {
            return;
        }
        try {
            // 投递口径（2026-10-01 拍板）：**登录级** —— 不再按邮箱认证过滤，
            // 游客提交的反馈同样收到处理回执（此前「仅已认证用户投递」会让游客的消息中心永久空转）。
            // is_read 由 notify 实现侧统一置 0（P0-1：feedback 不再 import / 构造 notify 实体）
            // B3：**回执文案按 type 分流** —— 举报（type=report）与反馈是两件事，
            // 一律写「反馈已处理 / 反馈未采纳」会让举报人读成语义错位。
            boolean isReport = FeedbackConst.TYPE_REPORT.equals(feedback.getType());
            String title = isReport ? (rejected ? "举报未采纳" : "举报已受理")
                    : (rejected ? "反馈未采纳" : "反馈已处理");
            // 受理时若顺带隐藏了被举报评价，必须说明（用户看不到评价了，不说明即为"凭空消失"）
            String hiddenSuffix = hiddenThisTime ? "，已隐藏该评价" : "";
            String body;
            if (rejected) {
                body = (isReport ? "你提交的举报未采纳：" : "你提交的反馈未采纳：") + rejectReason
                        + (reply == null ? "" : "。处理说明：" + reply);
            } else {
                body = (isReport ? "你提交的举报已受理" : "你提交的反馈已处理") + hiddenSuffix
                        + (reply == null ? "。" : "：" + reply);
            }
            // §7.16 origin：reply 必填时通知不存在「无回复」分支；B2 起 reply 可选 ⇒
            // 交由上面的 body 组装保证**始终有可读正文**，不产生空通知。
            notificationService.notify(new NotificationCmd(userId, title, body));
        } catch (Exception ignored) {
            // 回执失败不阻塞反馈处理
            //
            // D2 澄清（边界）：本 catch 只拦得住「向线程池提交任务」阶段的异常（如池已关闭），
            // **拦不住「异步线程内写库失败」**——@Async 下异步线程的异常不回传调用方。
            // 真正的写入失败由 NotificationServiceImpl#notify 内部 catch 就地记 error 日志。
            // 也就是说「不阻塞主流程」成立，但「失败可被调用方感知」不成立；
            // 排查丢通知只能看日志（该处已 log.error，不静默）。
        }
    }

    // ==================== 跨域写契约实现（P0-1：由本域 event 监听器调用） ====================

    /**
     * 账号归属迁移（原实现为 {@code AuthServiceImpl.migrateOwnership} 内的
     * {@code feedbackMapper.update(...)}，仅改 {@code user_feedback.user_id}，SQL 与语义逐字保留）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateOwnership(Long fromUserId, Long toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return 0;
        }
        return feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getUserId, fromUserId)
                .set(Feedback::getUserId, toUserId));
    }
}
