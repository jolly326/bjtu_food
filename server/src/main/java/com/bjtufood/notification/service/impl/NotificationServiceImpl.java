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
    public void notify(NotificationCmd cmd) {
        // 实体只在 notify 内部构造：isRead 恒 0（未读），投递方不再触达实体（P0-1 跨域契约收敛）
        Notification notification = new Notification();
        notification.setUserId(cmd.getUserId());
        notification.setType(cmd.getType());
        notification.setRelatedId(cmd.getRelatedId());
        notification.setIsRead(0);
        notification.setTitle(cmd.getTitle());
        notification.setContent(cmd.getContent());
        try {
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 异步线程内异常不会传播到调用方，必须就地记日志，否则写入失败将完全静默
            log.error("通知写入失败（userId={} type={} relatedId={}）",
                    cmd.getUserId(), cmd.getType(), cmd.getRelatedId(), e);
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
        // 错误码口径（docs/feature/client-系统通知.md）：不存在 → 4001（资源不存在）；归属不符 → 403
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
        // type / relatedId 端上零消费，不出参（见 NotificationVO 类注释）
        NotificationVO vo = new NotificationVO();
        vo.setId(n.getId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        // 存储 0/1 → 契约布尔（列不变，仅在 API 边界转换）
        vo.setIsRead(n.getIsRead() != null && n.getIsRead() == 1);
        vo.setCreatedAt(n.getCreatedAt());
        return vo;
    }
}
