package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.common.utils.UgcImageValidator;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.entity.Report;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.ReportMapper;
import com.bjtufood.feedback.service.FeedbackOptionService;
import com.bjtufood.feedback.service.FeedbackService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.service.ModerationTaskService;
import com.bjtufood.moderation.support.ModerationTaskFilters;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
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
 * 意见反馈服务实现。
 * <p>
 * 提交链路「先机审（无事务）→ 再落库（开事务）」：事务只包住落库一步，
 * 且<b>反馈行与处置任务行在同一事务</b>（见 {@link FeedbackPersister#insert}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    /**
     * 落库事务边界：写路径的 {@code @Transactional} 只包住**落库**本身
     * （「先机审（无事务）→ 再落库（开事务）」）—— 若事务从方法入口开始，会横跨微信机审的
     * HTTP 外呼（超时 5s），期间持续占用数据库连接；HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
     * <p>
     * 必须是**独立 Bean**：Spring 事务靠代理生效，同类的自调用不会开启事务。
     */
    private final FeedbackPersister feedbackPersister;
    /** 跨域只读契约：管理端「提交人」昵称投影 + 内容安检取 openid（P0-1，替代 UserMapper 直连） */
    private final UserService userService;
    private final LocalSensitiveFilter localSensitiveFilter;
    /** 微信内容安全检测 */
    private final ContentSecurityService contentSecurityService;
    private final ImageUrlUtil imageUrlUtil;
    /** 反馈类型字典真源（`feedback_option` 表）：提交白名单 + 管理端中文名翻译 */
    private final FeedbackOptionService feedbackOptionService;
    /** 处置状态机（`moderation_task`）：提交预建、处置归档、列表批量回填 */
    private final ModerationTaskService moderationTaskService;
    private final ReportMapper reportMapper;

    /**
     * 提交意见反馈。<b>本方法刻意不加 {@code @Transactional}</b>：事务边界收窄到落库一步
     * （{@link FeedbackPersister#insert}），使微信机审的外呼期间不占用数据库连接。
     */
    @Override
    public void submit(Long userId, FeedbackReq req) {
        // feedback.user_id 必填真实登录用户（外键 fk_feedback_user → user(id)）：无登录态即 401
        requireLogin(userId);
        if (!StringUtils.hasText(req.getContent())) {
            throw new BusinessException(400, "反馈内容不能为空");
        }
        // 类型值域 = feedback_option 表的**启用项**（字典可维护、免发版）：非法 / 已停用 → 400
        if (!feedbackOptionService.isSubmittable(req.getOptionId())) {
            throw new BusinessException(400, "反馈类型非法");
        }
        // 配图先校验（≤3 张 + COS 白名单）：避免为一个必然失败的请求白付一次微信机审额度
        String images = UgcImageValidator.encode(req.getImages(), "反馈", imageUrlUtil);

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setOptionId(req.getOptionId());
        feedback.setContent(localSensitiveFilter.filter(req.getContent()));
        feedback.setImages(images);

        // ---- 内容安全检测（全部 UGC 过微信内容安全检测）----
        // 文本 msgSecCheck v2（scene=2）；risky 由 checkText 统一拦截（400）；
        // 边界：无 openid 账号跳过机审放行（本地词库兜底）。
        checkUgcText(userId, feedback.getContent());
        feedbackPersister.insert(feedback);
    }

    /** 登录前置：feedback 的提交人恒为真实登录用户（user_id 必填 + 外键）。 */
    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    @Override
    public IPage<FeedbackAdminVO> listForAdmin(String status, Long optionId, Long userId, String keyword,
                                               int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        IPage<Feedback> p = feedbackMapper.selectPage(new Page<>(page, pageSize),
                buildAdminQuery(status, optionId, userId, keyword));

        List<Long> userIds = p.getRecords().stream()
                .map(Feedback::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        // 昵称投影经 auth 域只读契约下发（P0-1：本域不注入 UserMapper；空集合返回空 Map，不发起查询）
        Map<Long, String> userMap = userService.mapNameByIds(userIds);
        // 类型中文名（字典**全量含停用项**：历史反馈仍要能翻译出中文；逐页取一次）
        Map<Long, String> optionNames = feedbackOptionService.nameByIdAll();
        // 处置结果（处置文案真源在任务表）：按本页 ID 集合批量回填，一次 IN 查询
        Set<Long> ids = new HashSet<>();
        p.getRecords().forEach(f -> ids.add(f.getId()));
        Map<Long, ModerationTaskVO> tasks =
                moderationTaskService.mapByTypeAndIds(ModerationConst.TYPE_FEEDBACK, ids);

        return PageUtil.toVoPage(p,
                recs -> recs.stream().map(f -> toAdminVO(f, userMap, optionNames, tasks)).toList());
    }

    /**
     * 管理端列表查询构造：板块已取消（举报独立成表 / 独立端点），
     * 只剩「处置状态 + 类型 + 提交人 + 关键词」四个筛选，排序固定 `created_at DESC`。
     */
    private LambdaQueryWrapper<Feedback> buildAdminQuery(String status, Long optionId, Long userId, String keyword) {
        String normalizedStatus = ParamValidator.optionalInWhitelist(status, ModerationConst.STATUSES, "处理状态");
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(optionId != null, Feedback::getOptionId, optionId)
                .eq(userId != null, Feedback::getUserId, userId);
        // 「是否已处置」是**处置任务表**的状态：条件片段由 moderation 域下发（该域持有该表的 SQL 知识）
        if (StringUtils.hasText(normalizedStatus)) {
            ModerationTaskFilters.byStatus(wrapper, ModerationConst.TYPE_FEEDBACK, "feedback.id", normalizedStatus);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 关键词口径：反馈正文 **或** 处置文案（处置文案在任务表，条件片段由 moderation 域下发）
            wrapper.and(w -> w.like(Feedback::getContent, kw)
                    .or()
                    .apply(ModerationTaskFilters.replyKeywordCondition("feedback.id"),
                            ModerationConst.TYPE_FEEDBACK, ModerationTaskFilters.likePattern(kw)));
        }
        wrapper.orderByDesc(Feedback::getCreatedAt);
        return wrapper;
    }

    /** 管理端 VO 转换：补齐昵称、类型中文名、配图（JSON→绝对 URL 数组）与处置结果 */
    private FeedbackAdminVO toAdminVO(Feedback f, Map<Long, String> userMap,
                                      Map<Long, String> optionNames,
                                      Map<Long, ModerationTaskVO> tasks) {
        FeedbackAdminVO vo = new FeedbackAdminVO();
        vo.setId(f.getId());
        vo.setUserId(f.getUserId());
        vo.setUserName(userMap.get(f.getUserId()));
        vo.setOptionId(f.getOptionId());
        // 字典缺失（选项已被物理删除的极端情况）时回退 null：由前端按「—」呈现
        vo.setOptionName(f.getOptionId() == null ? null : optionNames.get(f.getOptionId()));
        vo.setContent(f.getContent() == null ? "" : f.getContent());
        List<String> images = JsonListUtil.parseStringList(f.getImages());
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        vo.setCreatedAt(f.getCreatedAt());
        applyTask(vo, tasks.get(f.getId()));
        return vo;
    }

    /** 处置结果回填（任务行缺失时保持 null，不伪造成「未处置」） */
    private static void applyTask(FeedbackAdminVO vo, ModerationTaskVO task) {
        if (task == null) {
            return;
        }
        vo.setStatus(task.getStatus());
        vo.setOutcome(task.getOutcome());
        vo.setReplyTitle(task.getReplyTitle());
        vo.setReplyContent(task.getReplyContent());
        vo.setHandledByName(task.getHandledByName());
        vo.setHandledAt(task.getHandledAt());
    }

    /**
     * 处置反馈。<b>本方法不加 {@code @Transactional}</b>：唯一的写动作（任务行归档 + 回执投递）
     * 已由 {@code ModerationTaskService#handle} 收敛在同一事务内，此处再包一层只会多一层空壳边界。
     */
    @Override
    public void handle(Long id, ModerationHandleReq req, Long adminId) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        moderationTaskService.handle(ModerationConst.TYPE_FEEDBACK, id,
                req.getOutcome(), req.getReplyTitle(), req.getReplyContent(), adminId, feedback.getUserId());
    }

    /**
     * 文本内容安全检测：登录用户取 openid 调 msgSecCheck v2（仅拦截，不落库安全态）。
     * <p>
     * 结果语义：risky 由 {@code checkText} 抛 400 拦截；pass 与 review（疑似）均视为放行。
     * 边界：取不到 openid 的账号（含用户不存在）无 openid 可用，内容安全检测内部按既有口径跳过放行。
     */
    private void checkUgcText(Long userId, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        // 只取 openid 判定要素（P0-1：本域不 import auth 实体/Mapper；
        // 用户不存在 ⇔ getAuthContext 返回 null ⇔ openid 为空）
        UserAuthContextVO user = userService.getAuthContext(userId);
        String openid = user == null ? null : user.getOpenid();
        contentSecurityService.checkText(openid, content, 2);
    }

    // ==================== 跨域写契约实现（P0-1：由本域 event 监听器调用） ====================

    /**
     * 账号归属迁移：跨域写侧解耦——跨域归属迁移由 auth 域发 {@code UserOwnershipMigratedEvent}，
     * 各域自行改本域表（auth 不直接改他域数据）。
     * <p>
     * 本域有**两张**用户提交表（`feedback` 与 `report`），故一次迁移内两张表一起改（同一事务）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateOwnership(Long fromUserId, Long toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return 0;
        }
        int feedbackAffected = feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getUserId, fromUserId)
                .set(Feedback::getUserId, toUserId));
        int reportAffected = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getUserId, fromUserId)
                .set(Report::getUserId, toUserId));
        return feedbackAffected + reportAffected;
    }
}
