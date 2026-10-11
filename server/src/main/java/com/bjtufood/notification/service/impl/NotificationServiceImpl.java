package com.bjtufood.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.notification.dto.NotificationVO;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.entity.Notification;
import com.bjtufood.notification.mapper.NotificationMapper;
import com.bjtufood.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 消息通知服务实现
 * <p>
 * 被审核/评论/👍等业务调用，异步解耦写入 notification 表。
 * P3/ARCH-008：学生端「我的通知」列表/未读数/已读逻辑自 Controller 下沉至此。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    /**
     * 写入一条通知（<b>开新事务</b>）。
     * <p>
     * BE-07：本方法补 {@code @Async("taskExecutor")}——事务边界收窄到异步线程，
     * 避免通知表慢/抖直接拖慢业务主流程（反馈处理）。
     * <p>
     * <b>D2 变更：移除 {@code Propagation.REQUIRES_NEW}，改回默认 {@code REQUIRED}</b>。
     * 原注解在此<b>既冗余又危险</b>：
     * <ul>
     *   <li><b>冗余</b>：{@code @Async} 方法运行在独立线程，线程池线程本身没有事务上下文，
     *       {@code REQUIRED} 同样会「无事务则新建」，与 REQUIRES_NEW 的实际效果一致；</li>
     *   <li><b>危险</b>：{@code AsyncConfig} 的拒绝策略是 {@code CallerRunsPolicy}，
     *       队列打满时任务<b>在调用方线程同步执行</b>——而调用方
     *       （{@code FeedbackServiceImpl#handle} / {@code CorrectionServiceImpl#adopt|reject}）
     *       正处于事务中。此时 REQUIRES_NEW 会<b>挂起外层事务再新开一条</b>，
     *       即单请求峰值占用 <b>2 条连接</b>。HikariCP 池上限仅 20
     *       （{@code application.yml}），并发写叠加队列打满即成倍放大连接需求，最坏自锁耗尽。
     *       改回 REQUIRED 后该场景并入调用方事务，<b>只占 1 条</b>。</li>
     * </ul>
     * <p>
     * <b>关于调用方 try-catch 的真实边界（D2 澄清）</b>：{@code @Async} 下异步线程内的异常
     * <b>不会</b>传播回调用方，故调用方的 try-catch 捕获不到「通知写入失败」——
     * 它只能拦住<b>提交任务阶段</b>的异常（如池已关闭）。
     * 真正的失败只由本方法内部 {@code catch} 记日志暴露（这也是它必须就地 log 的原因）。
     * 故此注释与调用方 {@code catch (Exception ignored)} 的语义须一并理解：
     * 「不阻塞主流程」成立，但「失败可被调用方感知」不成立。
     * <p>
     * @Async 依赖 Spring 代理，调用方必须经 {@link NotificationService} Bean 调用（禁止同类自调）。
     */
    @Override
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void notify(NotificationCmd cmd) {
        insert(cmd);
    }

    /**
     * 写入一条通知（**同步**，并入调用方事务）。
     * <p>
     * 与 {@link #notify} 的差别只在事务语义：本方法**不加 {@code @Async}**，
     * 走默认 {@code REQUIRED} 传播 ⇒ 并入调用方事务（如处置任务的
     * {@code ModerationTaskServiceImpl#handle}），使「任务行归档 + 回执落库」同生共死。
     * <p>
     * 不加 {@code REQUIRES_NEW}：那会挂起调用方事务另开一条连接，使单请求峰值占用 2 条连接
     * （池上限 20），并发写叠加即成倍放大连接需求（同 {@link #notify} 的 D2 结论）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyNow(NotificationCmd cmd) {
        insert(cmd);
    }

    /**
     * 落库一条通知（两入口共用）：实体只在这里构造，{@code isRead} 恒 0（未读）。
     * <p>
     * 写入失败一律就地记 error 后抛出：{@link #notify} 为 {@code @Async}，异常不回传调用方，
     * 日志是唯一可观测手段；{@link #notifyNow} 在调用方事务内，抛出后由调用方一并回滚。
     */
    private void insert(NotificationCmd cmd) {
        Notification notification = new Notification();
        notification.setUserId(cmd.getUserId());
        notification.setIsRead(0);
        notification.setReplyTitle(cmd.getReplyTitle());
        notification.setReplyContent(cmd.getReplyContent());
        try {
            notificationMapper.insert(notification);
        } catch (Exception e) {
            log.error("通知写入失败（userId={}）", cmd.getUserId(), e);
            throw e;
        }
    }

    @Override
    public PageResult<NotificationVO> listMy(Long userId, Boolean isRead, int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        if (isRead != null) {
            // 契约布尔 → 存储 0/1（列仍为 TINYINT，仅在 API 边界转换）
            wrapper.eq(Notification::getIsRead, isRead ? 1 : 0);
        }
        IPage<Notification> p = notificationMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<NotificationVO> records = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        // 错误码口径（docs/api/client/my.md）：不存在 → 4001（资源不存在）；归属不符 → 403
        if (n == null) {
            throw new BusinessException(4001, "通知不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能操作自己的通知");
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        // 单条批量 UPDATE：notification SET is_read = 1 WHERE user_id = ? AND is_read = 0
        // 数据隔离：仅当前用户；幂等：无未读时返回 0，不报错。
        Notification patch = new Notification();
        patch.setIsRead(1);
        return notificationMapper.update(patch, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setReplyTitle(n.getReplyTitle());
        vo.setReplyContent(n.getReplyContent());
        // 存储 0/1 → 契约布尔（列不变，仅在 API 边界转换）
        vo.setIsRead(n.getIsRead() != null && n.getIsRead() == 1);
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
