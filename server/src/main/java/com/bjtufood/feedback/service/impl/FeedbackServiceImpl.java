package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.constant.FeedbackConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.common.utils.UgcImageValidator;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackHandleReq;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.service.FeedbackService;
import com.bjtufood.notify.constant.NotificationConst;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈服务实现
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    /** 管理端列表补全「关联菜品名」用（DEV-04）；仅按 id 批量取 name，不参与反馈写入。 */
    private final DishMapper dishMapper;
    private final SensitiveFilter sensitiveFilter;
    private final NotificationService notificationService;
    private final ContentSecurityService contentSecurityService;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, FeedbackReq req) {
        // 类型写入白名单（P2-01 / P3-10）：仅端上真实产出的 4 类可写，
        // bug/other 为历史遗留、禁新增；非法值 400（不再原样落库）。
        String type = ParamValidator.requiredInWhitelist(req.getType(), FeedbackConst.WRITABLE_TYPES, "反馈类型");
        if (FeedbackConst.TYPE_REPORT.equals(type)) {
            // 举报必须关联被举报对象（当前举报对象为菜品详情的评价，复用 user_feedback 表）
            if (req.getRelatedId() == null
                    || !FeedbackConst.RELATED_REVIEW.equals(req.getRelatedType())) {
                throw new BusinessException("举报必须指定关联对象（relatedType=review 且 relatedId 必填）");
            }
            // 举报去重（project_spec §7.11 第 3 条，2026-09-14 用户拍板）：
            // 同一登录用户对同一被举报对象的重复举报不再新增记录，直接给业务提示。
            // 边界：游客举报（userId=null）无身份标识，不做去重（已登记备查）。
            if (userId != null && feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getUserId, userId)
                    .eq(Feedback::getType, FeedbackConst.TYPE_REPORT)
                    .eq(Feedback::getRelatedType, req.getRelatedType())
                    .eq(Feedback::getRelatedId, req.getRelatedId())) > 0) {
                throw new BusinessException("你已举报过该内容，我们会尽快处理，请勿重复提交");
            }
        }
        // 二级分类 sub（DEV-01 补全落库）：仅 type=suggestion（提个想法）有效——
        // 端上「想法/问题」二选一此前仅在请求中出现、未落库，现收敛为白名单并落库。
        // 1. type=suggestion：provided 但不在 SUB_WRITE_WHITELIST → 400（ParamValidator 统一口径，不静默降级）；
        //    未提供（null/空白）→ 按未填处理，落库 NULL。
        // 2. type != suggestion（2026-09-15 用户拍板，严格模式）：该值无效——
        //    未提供（null/空白）零影响（其他类型本就不传 sub，存量调用行为不变）；
        //    一旦提供（非空白）即 400，不再静默忽略，避免端上误传被吞掉而不自知。
        String sub;
        if (FeedbackConst.TYPE_SUGGESTION.equals(type)) {
            sub = ParamValidator.optionalInWhitelist(req.getSub(), FeedbackConst.SUB_WRITE_WHITELIST, "反馈二级分类");
        } else {
            if (StringUtils.hasText(req.getSub())) {
                throw new BusinessException(400, "反馈二级分类仅「提个想法」(suggestion) 类型有效");
            }
            sub = null;
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setSub(sub);
        feedback.setContent(sensitiveFilter.filter(req.getContent()));
        // contact 落库点已随 user_feedback.contact 列退役删除（2026-09-16 产品定型「不收集联系方式」）
        feedback.setRelatedType(req.getRelatedType());
        feedback.setRelatedId(req.getRelatedId());
        feedback.setStatus(FeedbackConst.STATUS_PENDING);

        // ---- 内容安全检测（产品定稿 2026-09-13：全部 UGC 过微信内容安全检测）----
        // 文本 msgSecCheck v2（scene=2）；risky 由 checkText 统一拦截（400）。
        // 2026-09-15 用户拍板「取消人工复核」：机检 review（疑似）归一为放行，
        // 不再落库 sec_state（该列已全链退役），故此处只保留拦截语义。
        // 边界（报告备案）：游客反馈（userId=null，PUB 接口）与 openid 为 NULL 的账号无法调 v2 接口
        // （msgSecCheck v2 openid 必填），按服务内既有口径跳过机检放行；微信凭据未配置（本地开发）同。
        checkUgcText(userId, feedback.getContent());
        feedback.setImages(UgcImageValidator.encode(req.getImages(), "反馈", imageUrlUtil));
        feedbackMapper.insert(feedback);
    }

    /**
     * 文本机检：登录用户取 openid 调 msgSecCheck v2（仅拦截，不落库安全态）。
     * <p>
     * 结果语义（2026-09-15 用户拍板取消人工复核）：risky 由 {@code checkText} 抛 400 拦截；
     * pass 与机检 review 均视为放行（sec_state 已全链退役，无待复核落库值）。
     * 边界：游客（userId=null）与无 openid 账号无 openid 可用，机检内部按既有口径跳过放行。
     */
    private void checkUgcText(Long userId, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        User user = userId == null ? null : userMapper.selectById(userId);
        String openid = user == null ? null : user.getOpenid();
        contentSecurityService.checkText(openid, content, 2);
    }

    @Override
    public IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, String keyword, int page, int pageSize) {
        int[] norm = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];

        // 查询入参白名单校验（P2-01 / PR-06）：非法值 400，不再静默进 SQL 恒空（掩盖真实积压）。
        // 兼容要求：type 白名单含历史遗留 bug/other（QUERY_TYPES），后台按历史类型筛选仍可查到老数据。
        // 内容安全态筛选入参已随 sec_state 全链退役删除（2026-09-15 取消人工复核，无复核队列）。
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
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                    .forEach(u -> userMap.put(u.getId(), u.getNickname()));
        }

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
        Map<Long, String> map = new HashMap<>(dishIds.size());
        // 只取 id/name 两列，避免拉取整行（含 images 等大字段）
        dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .select(Dish::getId, Dish::getName)
                        .in(Dish::getId, dishIds))
                .forEach(d -> map.put(d.getId(), d.getName()));
        return map;
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
        // contact 已随列退役（2026-09-16），管理端 VO 不再返回联系方式
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
        // §7.16（2026-09-14 用户拍板）：回复必填——学生收到的处理通知会展示该回复，空回复等于空通知。
        // 纯空白与 null 一律视为未填写：主流仍由 DTO 的 @NotBlank 在 Controller 层拦截（400）；
        // 此处为 Service 层兜底（同口径、同错误码 400），并统一 trim 后落库。
        String trimmedReply = req.getReply() == null ? null : req.getReply().trim();
        if (!StringUtils.hasText(trimmedReply)) {
            throw new BusinessException("请填写处理回复（学生将收到该内容）");
        }
        // §7.23 第 5 条（2026-09-15）：处理结论——handled=通过/已处理（缺省）；rejected=不采纳/退回。
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
        // 该列已于 2026-09-16 零消费退役删除（schema.sql drop_zero_consumer_columns），无需再处理。
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
            User user = userMapper.selectById(userId);
            if (user == null || user.getVerified() == null || user.getVerified() != 1) {
                return;
            }
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(NotificationConst.TYPE_FEEDBACK_HANDLE);
            n.setRelatedId(feedback.getId());
            n.setIsRead(0);
            n.setTitle(rejected ? "反馈未采纳" : "反馈已处理");
            // §7.16：reply 必填（handle 已保证非空白），通知不再存在「无回复」分支，一律携带回复正文；
            // §7.23 第 5 条：不采纳结论时回执必须带不采纳原因（handle 已保证非空白）。
            n.setContent(rejected
                    ? "你提交的反馈未采纳：" + rejectReason + "。处理说明：" + reply
                    : "你提交的反馈已处理：" + reply);
            notificationService.notify(n);
        } catch (Exception ignored) {
            // 回执失败不阻塞反馈处理
        }
    }
}
