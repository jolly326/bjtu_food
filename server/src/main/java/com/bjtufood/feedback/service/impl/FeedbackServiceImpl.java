package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
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
import com.bjtufood.feedback.dto.ReportReq;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.service.FeedbackService;
import com.bjtufood.notification.constant.NotificationConst;
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
    /** 跨域只读契约：管理端「提交人」昵称投影 + 回执投递的认证判据（P0-1，替代 UserMapper 直连） */
    private final UserService userService;
    /** 跨域只读契约：管理端列表补全「关联菜品名」用（DEV-04）；仅按 id 批量取 name，不参与反馈写入。 */
    private final DishService dishService;
    private final LocalSensitiveFilter localSensitiveFilter;
    private final NotificationService notificationService;
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
    public void report(Long userId, Long reviewId, ReportReq req) {
        // 举报目标必须存在且公开可见（已隐藏 / 已删除对外等价于不存在 → 4001）
        if (!reviewService.existsVisibleById(reviewId)) {
            throw new BusinessException(4001, "评价不存在");
        }
        // 举报原因必选（值域 = 字典白名单，PR-06：非法 / 缺失即 400，不静默降级）
        String reason = ParamValidator.requiredInWhitelist(req.getReason(), FeedbackConst.REPORT_REASON_VALUES, "举报原因");
        // 去重（§7.11 第 3 条）：同一登录用户对同一评价的重复举报不再新增（游客 userId=null 无身份标识，不去重）
        if (userId != null && feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .eq(Feedback::getType, FeedbackConst.TYPE_REPORT)
                .eq(Feedback::getRelatedType, FeedbackConst.RELATED_REVIEW)
                .eq(Feedback::getRelatedId, reviewId)) > 0) {
            throw new BusinessException(400, "你已举报过该内容，我们会尽快处理，请勿重复提交");
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
    public IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, String keyword, int page, int pageSize) {
        int[] norm = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];

        // 查询入参白名单校验（P2-01 / PR-06）：非法值 400，不再静默进 SQL 恒空（掩盖真实积压）。
        // 兼容要求：type 白名单含历史遗留 bug/other（QUERY_TYPES），后台按历史类型筛选仍可查到老数据。
        // 内容安全态筛选入参已随 sec_state 全链退役删除。
        status = ParamValidator.optionalInWhitelist(status, FeedbackConst.QUERY_STATUSES, "处理状态");
        type = ParamValidator.optionalInWhitelist(type, FeedbackConst.QUERY_TYPES, "反馈类型");

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(StringUtils.hasText(status), Feedback::getStatus, status)
                .eq(StringUtils.hasText(type), Feedback::getType, type)
                .eq(userId != null, Feedback::getUserId, userId);

        // 关键词模糊匹配反馈正文或管理员回复；用 and(...) 包一层括号，避免 OR 打散上面的等值条件。
        // 必须在 orderByDesc 之前追加，否则条件片段会拼到 ORDER BY 之后生成非法 SQL。
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Feedback::getContent, kw).or().like(Feedback::getReply, kw));
        }
        wrapper.orderByDesc(Feedback::getCreatedAt);

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

        IPage<FeedbackAdminVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(f -> toAdminVO(f, userMap, dishNameMap)).toList());
        return result;
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

    /** 管理端 VO 转换：补齐昵称、配图（JSON→数组）、关联菜品名（DEV-04）；内容安全态已随 sec_state 退役 */
    private FeedbackAdminVO toAdminVO(Feedback f, Map<Long, String> userMap, Map<Long, String> dishNameMap) {
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
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, FeedbackHandleReq req) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        // §7.16：回复必填——学生收到的处理通知会展示该回复，空回复等于空通知。
        // 纯空白与 null 一律视为未填写：主流仍由 DTO 的 @NotBlank 在 Controller 层拦截（400）；
        // 此处为 Service 层兜底（同口径、同错误码 400），并统一 trim 后落库。
        String trimmedReply = req.getReply() == null ? null : req.getReply().trim();
        if (!StringUtils.hasText(trimmedReply)) {
            throw new BusinessException("请填写处理回复（学生将收到该内容）");
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
        feedback.setStatus(FeedbackConst.STATUS_HANDLED);
        feedback.setReply(trimmedReply);
        feedback.setRejectReason(rejectReason);
        feedback.setHandledAt(LocalDateTime.now());
        // §7.10：管理端操作人身份降级（单口令即单人），handler_id 一直未写；
        // 该列已于零消费退役删除（schema.sql drop_zero_consumer_columns），无需再处理。
        feedbackMapper.updateById(feedback);
        // 处理结果回执（携带处理结论与不采纳原因）：仅向「可归属」提交人（提交时为已认证登录用户）投递
        sendFeedbackReceipt(feedback, rejected, trimmedReply, rejectReason);
    }

    /**
     * 反馈处理结果回执（§7.23 第 5 条：回执携带处理结论；不采纳/退回时一并展示不采纳原因）。
     * <p>
     * 归属判据：提交时带 userId（登录态）且该账号已邮箱认证（verified=1）。
     * 游客（userId 为空）与未认证账号不投递——反馈主路径刻意匿名，不保留可回执身份。
     * 投递失败不影响处理结果（独立 try 分支，异常不外抛到主流程）。
     *
     * @param rejected    true=处理结论为不采纳/退回（此时 rejectReason 非空，handle 已校验）
     * @param reply       处理回复（handle 已保证 trim 后非空白）
     * @param rejectReason 不采纳原因（rejected=true 时非空；否则为 null，不参与文案）
     */
    private void sendFeedbackReceipt(Feedback feedback, boolean rejected, String reply, String rejectReason) {
        Long userId = feedback.getUserId();
        if (userId == null) {
            return;
        }
        try {
            // 仅对已认证用户投递回执（判据 = bind_email 非空，唯一真源在 auth，
            // 经只读契约折算为布尔下发；用户不存在亦为 false，与原实现同效）
            if (!userService.isVerifiedById(userId)) {
                return;
            }
            // is_read 由 notify 实现侧统一置 0（P0-1：feedback 不再 import / 构造 notify 实体）
            // §7.16：reply 必填（handle 已保证非空白），通知不再存在「无回复」分支，一律携带回复正文；
            // §7.23 第 5 条：不采纳结论时回执必须带不采纳原因（handle 已保证非空白）。
            notificationService.notify(new NotificationCmd(userId, NotificationConst.TYPE_FEEDBACK_HANDLE,
                    feedback.getId(), rejected ? "反馈未采纳" : "反馈已处理",
                    rejected
                            ? "你提交的反馈未采纳：" + rejectReason + "。处理说明：" + reply
                            : "你提交的反馈已处理：" + reply));
        } catch (Exception ignored) {
            // 回执失败不阻塞反馈处理
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
