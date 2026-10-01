package com.bjtufood.notification.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.result.PageResult;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.dto.NotificationVO;
import com.bjtufood.notification.entity.Notification;
import com.bjtufood.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationServiceImpl} 单元测试。
 * <p>
 * 聚焦两点：
 * <ul>
 *   <li><b>数据隔离</b>：{@code markRead} 必须校验通知归属，非本人一律静默成功——
 *       这是最容易被改坏的越权防线（若退化为「查到就置已读」即成为横向越权）；</li>
 *   <li><b>失败可观测</b>：{@code notify} 因 {@code @Async} 异常不回传调用方，
 *       故其内部必须就地 log 后再抛，否则通知写入失败将完全静默。</li>
 * </ul>
 * 被测类为纯 POJO：{@code @Async} / {@code @Transactional} 依赖 Spring 代理，单测中不生效，
 * 断言的是<b>方法体内的业务逻辑</b>，与代理无关。
 */
class NotificationServiceImplTest {

    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(notificationMapper);
    }

    private static Notification notification(Long id, Long userId, int isRead) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setIsRead(isRead);
        return n;
    }

    // ==================== markRead：数据隔离 ====================

    @Test
    @DisplayName("标记他人通知为已读 → 403（归属不符）且不落库")
    void markingOthersNotificationIsForbidden() {
        when(notificationMapper.selectById(5L)).thenReturn(notification(5L, 2L, 0));

        assertThatThrownBy(() -> service().markRead(1L, 5L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(403));

        // 关键断言：非本人通知不得被改写
        verify(notificationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("标记不存在的通知 → 4001（资源不存在）且不落库")
    void markingMissingNotificationIsNotFound() {
        when(notificationMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> service().markRead(1L, 5L))
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4001));

        verify(notificationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("标记本人通知为已读 → 置 isRead=1 并落库")
    void markingOwnNotificationUpdates() {
        when(notificationMapper.selectById(5L)).thenReturn(notification(5L, 1L, 0));

        service().markRead(1L, 5L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsRead()).isEqualTo(1);
        assertThat(captor.getValue().getId()).isEqualTo(5L);
    }

    // ==================== markAllRead：批量仅限本人 ====================

    @Test
    @DisplayName("全部已读：按 userId + isRead=0 做单条批量 UPDATE，返回受影响行数")
    void markAllReadTargetsOnlyOwnUnread() {
        when(notificationMapper.update(any(), any())).thenReturn(3);

        int affected = service().markAllRead(1L);

        assertThat(affected).isEqualTo(3);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).update(captor.capture(), any());
        // 仅携带 isRead 补丁，不带 userId / id（避免误更新他人行）
        assertThat(captor.getValue().getIsRead()).isEqualTo(1);
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getId()).isNull();
    }

    // ==================== notify：失败可观测 ====================

    @Test
    @DisplayName("notify：实体仅在内部构造，isRead 恒 0（投递方不触达实体）")
    void notifyBuildsEntityInternally() {
        NotificationCmd cmd = new NotificationCmd();
        cmd.setUserId(1L);
        cmd.setType("feedback_handled");
        cmd.setRelatedId(7L);
        cmd.setTitle("反馈已处理");
        cmd.setContent("管理员已回复");

        service().notify(cmd);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo("feedback_handled");
        assertThat(saved.getRelatedId()).isEqualTo(7L);
        assertThat(saved.getTitle()).isEqualTo("反馈已处理");
        assertThat(saved.getContent()).isEqualTo("管理员已回复");
        assertThat(saved.getIsRead()).as("新投递通知恒为未读").isZero();
    }

    @Test
    @DisplayName("notify：写入失败必须抛出（不得静默吞掉）——@Async 下异常不回传调用方，就地抛+日志是唯一可观测手段")
    void notifyPropagatesInsertFailure() {
        when(notificationMapper.insert(any())).thenThrow(new RuntimeException("db down"));
        NotificationCmd cmd = new NotificationCmd();
        cmd.setUserId(1L);

        assertThatThrownBy(() -> service().notify(cmd))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }

    // ==================== countUnread / listMy ====================

    @Test
    @DisplayName("countUnread：未读计数按 userId + isRead=0 过滤")
    void countUnreadDelegatesToMapper() {
        when(notificationMapper.selectCount(any())).thenReturn(4L);

        assertThat(service().countUnread(1L)).isEqualTo(4L);
        verify(notificationMapper).selectCount(any());
    }

    @Test
    @DisplayName("listMy：非法分页参数被 PageUtil 归一，不抛异常")
    void listMyNormalizesPagination() {
        when(notificationMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Notification>(1, 10)
                        .setRecords(java.util.List.of(notification(1L, 1L, 0)))
                        .setTotal(1));

        PageResult<NotificationVO> result = service().listMy(1L, null, -1, 0);

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(1L);
    }
}
