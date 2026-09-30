package com.bjtufood.feedback.service.impl;

import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 反馈落库（**最小化事务边界**）。
 * <p>
 * 背景（2026-09-29 性能修正）：此前 {@code FeedbackServiceImpl#submit} / {@code #report} 直接标注
 * {@code @Transactional} —— 事务从**方法入口**就开始，横跨「微信内容安全检测」这一次外部 HTTP 外呼
 * （连接/读取超时 5s）。期间数据库连接被持续占用；而 HikariCP 默认池仅 10 条，并发稍高即被占满，
 * 连纯只读的首页 / 详情请求都会被拖住（连接池雪崩）。
 * <p>
 * 修法：<b>先机审（无事务、不占连接）→ 再落库（才开事务）</b>。为此把落库动作收敛到本类，
 * 由它持有唯一的 {@code @Transactional}；主 Service 的写方法不再标注事务。
 * <p>
 * <b>为何必须是独立 Bean 而不是本类的 private 方法</b>：Spring 的声明式事务靠<b>代理</b>生效，
 * 同一类内自调用（{@code this.insert(...)}）<b>不经过代理 ⇒ 事务根本不会开启</b>。
 * 故落库必须落在另一个 Bean 上，由主 Service 注入调用。
 * <p>
 * 可见行为不变：落库字段值、错误码、失败语义均与原实现一致（由 {@code FeedbackServiceImplTest} 逐条断言）。
 */
@Component
@RequiredArgsConstructor
public class FeedbackPersister {

    private final FeedbackMapper feedbackMapper;

    /**
     * 落库一条反馈 / 举报（事务边界仅包住这一次 insert）。
     *
     * @param feedback 已通过全部校验与机审的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(Feedback feedback) {
        feedbackMapper.insert(feedback);
    }
}
