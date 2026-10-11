package com.bjtufood.feedback.service.impl;

import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.service.ModerationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 反馈落库（**最小化事务边界**）。
 * <p>
 * 边界：事务从方法入口开始会横跨「微信内容安全检测」的外部 HTTP 外呼（连接/读取超时 5s），
 * 期间持续占用数据库连接；HikariCP 默认池仅 10 条，并发稍高即被占满，连纯只读的首页 / 详情
 * 请求都会被拖住（连接池雪崩）。
 * <p>
 * 口径：<b>先机审（无事务、不占连接）→ 再落库（才开事务）</b> —— 落库动作收敛到本类，
 * 由它持有唯一的 {@code @Transactional}；主 Service 的写方法不标注事务。
 * <p>
 * 🔴 <b>事务内写两行</b>：反馈行 + 处置任务行（{@code status=pending}）。「是否已处置」是显式行，
 * 管理端列表才能按状态走索引；两行必须同生共死 —— 少写任务行会让该反馈从管理端列表「消失」，
 * 只写任务行则是孤儿任务。
 * <p>
 * <b>为何必须是独立 Bean 而不是本类的 private 方法</b>：Spring 的声明式事务靠<b>代理</b>生效，
 * 同一类内自调用（{@code this.insert(...)}）<b>不经过代理 ⇒ 事务根本不会开启</b>。
 * 故落库必须落在另一个 Bean 上，由主 Service 注入调用。
 */
@Component
@RequiredArgsConstructor
public class FeedbackPersister {

    private final FeedbackMapper feedbackMapper;
    /** 处置任务写入契约（同一事务内预建：见类注释） */
    private final ModerationTaskService moderationTaskService;

    /**
     * 落库一条反馈（事务边界包住「反馈行 + 处置任务行」两次 insert）。
     *
     * @param feedback 已通过全部校验与机审的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(Feedback feedback) {
        feedbackMapper.insert(feedback);
        moderationTaskService.create(ModerationConst.TYPE_FEEDBACK, feedback.getId());
    }
}
