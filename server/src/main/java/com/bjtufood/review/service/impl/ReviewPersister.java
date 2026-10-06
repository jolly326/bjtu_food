package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评价落库（**最小化事务边界**）。
 * <p>
 * 边界：事务若从方法入口开始，会横跨「微信内容安全检测」的外部 HTTP 外呼（超时 5s），
 * 期间持续占用数据库连接；HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
 * <p>
 * 口径：<b>先机审（无事务、不占连接）→ 再落库（才开事务）</b>。
 * <p>
 * ⚠️ <b>为何本类必须同时承载「写库」与「发事件」</b>：
 * {@code RatingUpdateListener} 用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 异步重算菜品均分。
 * 若把 {@code ReviewSubmittedEvent} 的发布挪到<b>事务之外</b>，Spring 会因无活动事务而
 * <b>不触发</b>该监听器（默认 {@code fallbackExecution=false}）⇒ 菜品评分<b>永不重算</b>，
 * 用户将<b>直接看到错误评分</b>（🔴 2026-10-05 热度下线后，排序不再受评分影响，但出参评分仍错）。故事件发布必须与写库同处一个事务，
 * 由本类的 {@code @Transactional} 一并包住。
 * <p>
 * 必须是**独立 Bean**：Spring 声明式事务靠<b>代理</b>生效，同类自调用不会开启事务。
 */
@Component
@RequiredArgsConstructor
public class ReviewPersister {

    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 首次发表：插入 + 发布重算事件（同一事务内）。
     *
     * @param review 已通过准入与机审的评价实体（插入后回填 id）
     * @param dishId 所属菜品（事件载荷）
     * @param rating 评分（事件载荷）
     */
    @Transactional(rollbackFor = Exception.class)
    public void insertAndPublish(Review review, Long dishId, Integer rating) {
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // uk_review_user_dish 唯一键兜底并发竞态：前置 selectCount 通过但插入瞬间已被抢先落库
            throw new BusinessException("您已评价过该菜品");
        }
        // 必须写在事务内：监听器为 AFTER_COMMIT 相位（见类注释）
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, dishId, rating));
    }

    /**
     * 重新评价：覆盖同一行 + 发布重算事件（同一事务内）。
     * 覆盖语义：评分 / 文字 / 配图显式 set、{@code is_hidden} 重置 0、
     * 🔴 <b>{@code created_at} 保留原值不刷新</b>（首次评价时间即固定位置，改评价不置顶）。
     *
     * @param id        评价 ID
     * @param rating    新评分
     * @param content   已过本地词库的文本
     * @param imagesJson 配图 JSON（可为 null）
     * @param dishId    所属菜品（事件载荷）
     */
    // 🔴 覆盖更新**不刷新 `created_at`**：首次评价时间即该评价的固定位置，
    // 反复修改无法把自己刷到列表顶部（时间倒序下位置不动）⇒ 无需额外的提交限频。
    // is_hidden 重置 0：重新评价即恢复可见。
    @Transactional(rollbackFor = Exception.class)
    public void updateAndPublish(Long id, Integer rating, String content, String imagesJson, Long dishId) {
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .set(Review::getRating, rating)
                .set(Review::getContent, content)
                .set(Review::getImages, imagesJson)
                .set(Review::getIsHidden, 0));
        // 必须写在事务内：监听器为 AFTER_COMMIT 相位（见类注释）
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, dishId, rating));
    }
}
