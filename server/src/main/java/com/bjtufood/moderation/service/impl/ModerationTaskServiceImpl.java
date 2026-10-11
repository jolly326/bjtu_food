package com.bjtufood.moderation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.service.AdminAccountService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.entity.ModerationTask;
import com.bjtufood.moderation.mapper.ModerationTaskMapper;
import com.bjtufood.moderation.service.ModerationTaskService;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理端处置任务服务实现。
 * <p>
 * 两处事务边界都是「一次动作一个事务」，且都与相邻写动作同生共死：
 * <ul>
 *   <li>{@link #create}：由工单落库 Bean 调用 ⇒ 并入其事务（与工单 INSERT 同生共死）；</li>
 *   <li>{@link #handle}：任务行 UPDATE + 站内回执 INSERT 同事务 —— 回执文案是**快照**，
 *       与任务行同句两份，不允许「任务已归档、用户没收到回执」的中间态。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ModerationTaskServiceImpl implements ModerationTaskService {

    private final ModerationTaskMapper moderationTaskMapper;
    /** 处置人登录名投影（管理端列表展示「处理人」） */
    private final AdminAccountService adminAccountService;
    /** 站内回执投递（同步、并入本事务：快照与任务行同生共死） */
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(String type, Long typeId) {
        String normalizedType = ParamValidator.requiredInWhitelist(type, ModerationConst.TYPES, "工单类型");
        if (typeId == null) {
            throw new BusinessException("工单 ID 不能为空");
        }
        ModerationTask task = new ModerationTask();
        task.setType(normalizedType);
        task.setTypeId(typeId);
        task.setStatus(ModerationConst.STATUS_PENDING);
        // outcome / replyTitle / replyContent / handledBy / handledAt 保持 NULL（未处置即无结论与文案）
        moderationTaskMapper.insert(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(String type, Long typeId, String outcome, String replyTitle, String replyContent,
                       Long adminId, Long recipientUserId) {
        String normalizedType = ParamValidator.requiredInWhitelist(type, ModerationConst.TYPES, "工单类型");
        String normalizedOutcome = resolveOutcome(outcome);
        boolean rejected = ModerationConst.OUTCOME_REJECTED.equals(normalizedOutcome);

        // 不采纳必须写清原因：留空 / 纯空白 → 400（不得静默降级为缺省文案）
        String trimmedContent = trimToNull(replyContent);
        if (rejected && trimmedContent == null) {
            throw new BusinessException(400, "请填写不采纳原因");
        }
        // 缺省文案兜底：回执正文**始终有可读内容**（不得出现空回执）
        String finalTitle = trimToNull(replyTitle);
        if (finalTitle == null) {
            finalTitle = defaultTitle(normalizedType, normalizedOutcome);
        } else if (finalTitle.length() > ModerationConst.REPLY_TITLE_MAX_LENGTH) {
            throw new BusinessException(400, "回执标题不能超过" + ModerationConst.REPLY_TITLE_MAX_LENGTH + "字");
        }
        String finalContent = trimmedContent == null ? defaultContent(normalizedType, normalizedOutcome) : trimmedContent;
        if (finalContent.length() > ModerationConst.REPLY_CONTENT_MAX_LENGTH) {
            throw new BusinessException(400, "处置文案不能超过" + ModerationConst.REPLY_CONTENT_MAX_LENGTH + "字");
        }

        ModerationTask task = findOne(normalizedType, typeId);
        if (task == null) {
            throw new BusinessException(4001, "处置任务不存在");
        }
        // 已处置再处置 → 400：防处置结论被静默改写 + 回执重复投递
        if (!ModerationConst.STATUS_PENDING.equals(task.getStatus())) {
            throw new BusinessException(400, "该记录已处理");
        }

        task.setStatus(ModerationConst.STATUS_HANDLED);
        task.setOutcome(normalizedOutcome);
        task.setReplyTitle(finalTitle);
        task.setReplyContent(finalContent);
        task.setHandledBy(adminId);
        task.setHandledAt(LocalDateTime.now());
        moderationTaskMapper.updateById(task);

        // 站内回执（登录级投递：提交恒带登录态 ⇒ 恒有收件人；null 为防御性兜底）
        if (recipientUserId != null) {
            notificationService.notifyNow(new NotificationCmd(recipientUserId, finalTitle, finalContent));
        }
    }

    @Override
    public ModerationTaskVO findByTypeAndId(String type, Long typeId) {
        return toVO(findOne(type, typeId));
    }

    @Override
    public Map<Long, ModerationTaskVO> mapByTypeAndIds(String type, Collection<Long> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return Map.of();
        }
        List<ModerationTask> tasks = moderationTaskMapper.selectList(new LambdaQueryWrapper<ModerationTask>()
                .eq(ModerationTask::getType, type)
                .in(ModerationTask::getTypeId, typeIds));
        if (tasks.isEmpty()) {
            return Map.of();
        }
        // 处置人登录名：一次 IN 查询补齐（处置人已被删除时该行退化为 null，不阻塞列表）
        List<Long> adminIds = tasks.stream()
                .map(ModerationTask::getHandledBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> adminNames = adminAccountService.mapUsernameByIds(adminIds);

        Map<Long, ModerationTaskVO> result = new HashMap<>();
        for (ModerationTask task : tasks) {
            ModerationTaskVO vo = new ModerationTaskVO();
            vo.setStatus(task.getStatus());
            vo.setOutcome(task.getOutcome());
            vo.setReplyTitle(task.getReplyTitle());
            vo.setReplyContent(task.getReplyContent());
            vo.setHandledByName(task.getHandledBy() == null ? null : adminNames.get(task.getHandledBy()));
            vo.setHandledAt(task.getHandledAt());
            result.put(task.getTypeId(), vo);
        }
        return result;
    }

    /** 单条投影（处置人登录名按单条回查；仅前置判据与回显用，列表路径走 {@link #mapByTypeAndIds} 批量口径） */
    private ModerationTaskVO toVO(ModerationTask task) {
        if (task == null) {
            return null;
        }
        ModerationTaskVO vo = new ModerationTaskVO();
        vo.setStatus(task.getStatus());
        vo.setOutcome(task.getOutcome());
        vo.setReplyTitle(task.getReplyTitle());
        vo.setReplyContent(task.getReplyContent());
        vo.setHandledByName(task.getHandledBy() == null ? null
                : adminAccountService.mapUsernameByIds(List.of(task.getHandledBy())).get(task.getHandledBy()));
        vo.setHandledAt(task.getHandledAt());
        return vo;
    }

    @Override
    public long countPending(String type) {
        return moderationTaskMapper.selectCount(new LambdaQueryWrapper<ModerationTask>()
                .eq(ModerationTask::getType, type)
                .eq(ModerationTask::getStatus, ModerationConst.STATUS_PENDING));
    }

    // ==================== 内部工具 ====================

    /** 按多态键取任务行（唯一索引 {@code (type, type_id)} 保证至多一行） */
    private ModerationTask findOne(String type, Long typeId) {
        if (typeId == null) {
            return null;
        }
        return moderationTaskMapper.selectOne(new LambdaQueryWrapper<ModerationTask>()
                .eq(ModerationTask::getType, type)
                .eq(ModerationTask::getTypeId, typeId));
    }

    /** 处置结论归一：null / 空白按 handled 缺省；白名单外一律 400（不静默降级） */
    private static String resolveOutcome(String outcome) {
        if (!StringUtils.hasText(outcome)) {
            return ModerationConst.OUTCOME_HANDLED;
        }
        return ParamValidator.requiredInWhitelist(outcome, ModerationConst.OUTCOMES, "处理结论");
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 缺省回执短标题（按工单类型 + 结论）。
     * <p>
     * 工单属主域有更贴切的措辞时可显式传入（如菜品已下架、菜品仍在售），本表只作兜底。
     */
    private static String defaultTitle(String type, String outcome) {
        boolean rejected = ModerationConst.OUTCOME_REJECTED.equals(outcome);
        return switch (type) {
            case ModerationConst.TYPE_REPORT -> rejected ? "举报未采纳" : "举报已受理";
            case ModerationConst.TYPE_CORRECTION -> rejected ? "菜品信息未采纳" : "菜品信息已更新";
            default -> rejected ? "反馈未采纳" : "反馈已处理";
        };
    }

    /** 缺省回执正文（按工单类型 + 结论）；与缺省标题同源，保证回执可读 */
    private static String defaultContent(String type, String outcome) {
        boolean rejected = ModerationConst.OUTCOME_REJECTED.equals(outcome);
        return switch (type) {
            case ModerationConst.TYPE_REPORT -> rejected ? "你提交的举报未采纳。" : "你提交的举报已受理。";
            case ModerationConst.TYPE_CORRECTION -> rejected ? "你提交的菜品信息未采纳。" : "你提交的菜品信息纠错已采纳。";
            default -> rejected ? "你提交的反馈未采纳。" : "你提交的反馈已处理。";
        };
    }
}
