package com.bjtufood.review.event;

import com.bjtufood.auth.event.UserOwnershipMigratedEvent;
import com.bjtufood.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号归属迁移 → 评价改挂（P0-1 写侧解耦，替代原 {@code AuthServiceImpl.migrateOwnership}
 * 内直接写 review 表的两条 UPDATE）。
 * <p>
 * 「先清冲突行、再改归属」的顺序依赖 review 的唯一键 {@code uk_review_user_dish}
 * （同一 user+dish 只能有一行），属 review 域自有知识，故收敛在
 * {@link ReviewService#migrateOwnership(Long, Long)} 一处，auth 侧不再知晓。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewOwnershipListener {

    private final ReviewService reviewService;

    @EventListener
    public void onUserOwnershipMigrated(UserOwnershipMigratedEvent event) {
        int affected = reviewService.migrateOwnership(event.fromUserId(), event.toUserId());
        if (affected > 0) {
            log.info("账号归属迁移评价改挂 fromUserId={} toUserId={} affected={}",
                    event.fromUserId(), event.toUserId(), affected);
        }
    }
}

