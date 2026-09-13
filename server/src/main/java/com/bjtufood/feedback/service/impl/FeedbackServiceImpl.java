package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.constant.FeedbackConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.content.security.SecSuggest;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
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

    /** 内容安全状态常量：与 ReviewServiceImpl 口径一致 */
    public static final String SEC_STATE_PASS = "pass";
    public static final String SEC_STATE_REVIEW = "review";
    public static final String SEC_STATE_REJECTED = "rejected";

    /** UGC 配图上限（张） */
    private static final int MAX_IMAGES = 3;

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final SensitiveFilter sensitiveFilter;
    private final NotificationService notificationService;
    private final ContentSecurityService contentSecurityService;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, FeedbackReq req) {
        if (FeedbackConst.TYPE_REPORT.equals(req.getType())) {
            // 举报必须关联被举报对象（当前举报对象为菜品详情的评价，复用 user_feedback 表）
            if (req.getRelatedId() == null
                    || !FeedbackConst.RELATED_REVIEW.equals(req.getRelatedType())) {
                throw new BusinessException("举报必须指定关联对象（relatedType=review 且 relatedId 必填）");
            }
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(req.getType());
        feedback.setContent(sensitiveFilter.filter(req.getContent()));
        feedback.setContact(req.getContact());
        feedback.setRelatedType(req.getRelatedType());
        feedback.setRelatedId(req.getRelatedId());
        feedback.setStatus(FeedbackConst.STATUS_PENDING);

        // ---- 内容安全检测（产品定稿 2026-09-13：全部 UGC 过微信内容安全检测）----
        // 文本 msgSecCheck v2（scene=2）；risky 由 checkText 统一拦截（400）。
        // 边界（project_spec §7.7，2026-09-14 修订：不再静默放行）：
        // 1. 游客反馈（userId=null，PUB 接口）与登录但 openid 为 NULL 的账号 → 无法调 v2 接口，
        //    一律落库 sec_state='review' 进管理端人工复核队列（反馈不公开展示，先落库后复核）；
        // 2. 微信凭据未配置（本地开发环境）→ 机检内部跳过返回 pass，生产必须配置。
        // 反馈无公开展示，sec_state 仅作管理端复核标记。
        feedback.setSecState(checkUgcText(userId, feedback.getContent()));
        feedback.setImages(encodeImages(req.getImages()));
        feedbackMapper.insert(feedback);
    }

    /** 文本机检：登录用户取 openid 调 msgSecCheck v2，review 态落库 sec_state='review' */
    private String checkUgcText(Long userId, String content) {
        if (!StringUtils.hasText(content)) {
            return SEC_STATE_PASS;
        }
        if (userId == null) {
            // 游客反馈（PUB 接口）无用户身份、无可信 openid：不静默放行，落库待人工复核
            return SEC_STATE_REVIEW;
        }
        User user = userMapper.selectById(userId);
        String openid = user == null ? null : user.getOpenid();
        if (!StringUtils.hasText(openid)) {
            // 无 openid（历史学号 / 邮箱账号）：msgSecCheck v2 无法调用，
            // 按 §7.7 不跳过放行，落库标记 review 进管理端人工复核队列。
            return SEC_STATE_REVIEW;
        }
        SecSuggest suggest = contentSecurityService.checkText(openid, content, 2);
        return suggest == SecSuggest.REVIEW ? SEC_STATE_REVIEW : SEC_STATE_PASS;
    }

    /**
     * 校验并序列化反馈配图：≤3 张、每项必须为受信任的 COS 绝对地址（与评价口径一致）。
     */
    private String encodeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException("反馈配图最多 " + MAX_IMAGES + " 张");
        }
        List<String> normalized = images.stream().map(String::trim).filter(StringUtils::hasText).toList();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MAX_IMAGES) {
            throw new BusinessException("反馈配图最多 " + MAX_IMAGES + " 张");
        }
        for (String url : normalized) {
            if (!imageUrlUtil.isValidCosUgcUrl(url)) {
                throw new BusinessException("图片地址不合法，请重新上传");
            }
        }
        return JsonListUtil.toJson(normalized);
    }

    @Override
    public IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, String secState, String keyword, int page, int pageSize) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(StringUtils.hasText(status), Feedback::getStatus, status)
                .eq(StringUtils.hasText(type), Feedback::getType, type)
                .eq(StringUtils.hasText(secState), Feedback::getSecState,
                        secState == null ? null : secState.trim().toLowerCase())
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

        IPage<FeedbackAdminVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(f -> toAdminVO(f, userMap)).toList());
        return result;
    }

    /** 管理端 VO 转换：补齐昵称、配图（JSON→数组）、内容安全状态 */
    private FeedbackAdminVO toAdminVO(Feedback f, Map<Long, String> userMap) {
        FeedbackAdminVO vo = new FeedbackAdminVO();
        vo.setId(f.getId());
        vo.setUserId(f.getUserId());
        vo.setUserNickname(userMap.get(f.getUserId()));
        vo.setType(f.getType());
        vo.setContent(f.getContent());
        List<String> images = JsonListUtil.parseStringList(f.getImages());
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        vo.setSecState(StringUtils.hasText(f.getSecState()) ? f.getSecState() : SEC_STATE_PASS);
        vo.setContact(f.getContact());
        vo.setRelatedType(f.getRelatedType());
        vo.setRelatedId(f.getRelatedId());
        vo.setStatus(f.getStatus());
        vo.setReply(f.getReply());
        vo.setCreatedAt(f.getCreatedAt());
        vo.setHandledAt(f.getHandledAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, Long handlerId, String reply) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        feedback.setStatus(FeedbackConst.STATUS_HANDLED);
        feedback.setReply(reply);
        feedback.setHandledAt(LocalDateTime.now());
        feedback.setHandlerId(handlerId);
        feedbackMapper.updateById(feedback);
        // 处理结果回执：仅向「可归属」提交人（提交时为已认证登录用户）投递
        sendFeedbackReceipt(feedback);
    }

    /**
     * 反馈处理结果回执。
     * <p>
     * 归属判据：提交时带 userId（登录态）且该账号已邮箱认证（verified=1）。
     * 游客（userId 为空）与未认证账号不投递——反馈主路径刻意匿名，不保留可回执身份。
     * 投递失败不影响处理结果（独立 try 分支，异常不外抛到主流程）。
     */
    private void sendFeedbackReceipt(Feedback feedback) {
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
            n.setTitle("反馈已处理");
            n.setContent(StringUtils.hasText(feedback.getReply())
                    ? "你提交的反馈已处理：" + feedback.getReply()
                    : "你提交的反馈我们已处理完毕，感谢你的反馈！");
            notificationService.notify(n);
        } catch (Exception ignored) {
            // 回执失败不阻塞反馈处理
        }
    }
}
