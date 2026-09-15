package com.bjtufood.notify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.notify.dto.NotificationVO;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.mapper.NotificationMapper;
import com.bjtufood.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
     * 写入一条通知。
     * <p>
     * BE-07：补 {@code @Async("taskExecutor")}——此前仅靠 REQUIRES_NEW 开新事务，
     * 写入仍发生在调用方请求线程上，通知表慢/抖会直接拖慢业务主流程（反馈处理）。
     * 现按既定规约走 {@code common/config/AsyncConfig} 的有界线程池（core 4 / max 8 / queue 128 / CallerRuns）。
     * <p>
     * 注意：@Async 依赖 Spring 代理，调用方必须经 {@link NotificationService} Bean 调用（禁止同类自调）；
     * 且方法返回 void，异步线程内的异常不会回传调用方——调用方原有的 try-catch 兜底保持不变（更稳）。
     * REQUIRES_NEW 保留：异步线程内独立事务，不并入调用方事务。
     */
    @Override
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void notify(Notification notification) {
        try {
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 异步线程内异常不会传播到调用方，必须就地记日志，否则写入失败将完全静默
            log.error("通知写入失败（userId={} type={} relatedId={}）",
                    notification.getUserId(), notification.getType(), notification.getRelatedId(), e);
            throw e;
        }
    }

    @Override
    public PageResult<NotificationVO> listMy(Long userId, Integer isRead, int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        IPage<Notification> p = notificationMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<NotificationVO> records = p.getRecords().stream().map(this::toVO).toList();
        // page/pageSize 传归一化后的实际生效值（前置 PageUtil.normalize 已写回局部变量）
        return PageResult.of(records, p.getTotal(), page, pageSize);
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
        if (n == null || !n.getUserId().equals(userId)) {
            // 不存在或非本人：静默成功（幂等，且不暴露他人通知存在性）
            return;
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
        vo.setType(n.getType());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setRelatedId(n.getRelatedId());
        vo.setIsRead(n.getIsRead());
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
