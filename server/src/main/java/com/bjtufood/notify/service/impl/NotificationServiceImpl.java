package com.bjtufood.notify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.common.util.PageUtil;
import com.bjtufood.notify.dto.NotificationVO;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.mapper.NotificationMapper;
import com.bjtufood.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void notify(Notification notification) {
        notificationMapper.insert(notification);
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
        return PageResult.of(records, p.getTotal());
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
