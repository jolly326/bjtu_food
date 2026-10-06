package com.bjtufood.dish.task;

import com.bjtufood.dish.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 菜品评分聚合<b>对账任务</b>（D3）。
 * <p>
 * <b>为什么需要它</b>：{@code dish.avg_rating} / {@code dish.rating_count} 是<b>缓存列</b>，
 * 其正确性依赖 {@code RatingUpdateListener}（{@code @Async} + {@code AFTER_COMMIT}）的异步重算。
 * 该链路<b>丢事件即永久漂移</b>——进程重启、线程池 {@code CallerRunsPolicy} 同步执行时抛异常、
 * DB 抖动等，任一情况都会让该菜品评分停在上一个值；原监听器的处理只是 {@code log.error("[ALERT] 评分重算失败，需人工补偿")}，
 * 即<b>把正确性寄托在人工</b>上。
 * <p>
 * 漂移的代价不是「数字不好看」：{@code dish.avg_rating}/{@code rating_count} 是**详情页与列表出参的评分来源**，
 * 漂移会让用户**直接看到错误评分**，而系统不报错、只被异步重算或本任务「悄悄修正」，用户与客服都无从察觉。
 * （🔴 这两列不参与任何排序 —— 7 个视图走会话伪随机序；漂移危害为「评分展示错误」，兜底必要性不变。）
 * <p>
 * <b>本任务即补偿手段</b>：每日低频全量重算，把「人工补偿」变成「自动兜底」。
 * {@code recalcRatingBySubquery} 是<b>幂等全量重算</b>（子查询 AVG/COUNT 整体写回），
 * 重复执行结果相同，故对账无需比对差异、无需担心与在线写入互相覆盖——
 * 最坏情况是「对账算完又被一次并发写入覆盖」，下一次对账仍会修正。
 * <p>
 * <b>设计取舍</b>：
 * <ul>
 *   <li><b>每日一次</b>而非更频繁：漂移的代价是排序失真（缓慢、非致命），不值得为此高频全表重算；
 *       真正需要实时准确性的读路径已在 {@code ReviewSubmittedEvent} 链路里同步修正过了。</li>
 *   <li><b>分批游标</b>推进（{@code id > lastId LIMIT n}）而非一次性全量：
 *       对账是长任务，一次性取全部 ID 会在小内存实例（云托管 0.5G）上造成不必要的瞬时占用；
 *       且分批后单批失败只影响该批，下一次对账仍会覆盖。</li>
 *   <li><b>异常必须内部消化</b>：调度线程抛异常会导致该任务后续不再被触发（与
 *       {@code EmailVerificationCodeCleanupTask} 同口径）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DishRatingReconcileTask {

    private final DishMapper dishMapper;

    /**
     * 单批扫描行数。
     * <p>
     * 取 200：单批内存与批次数之间的折中，
     * 兼顾「批次数不过多」与「单批内存可控」。每批都是一条极轻的
     * {@code SELECT id ... WHERE rating_count > 0 AND id > ? ORDER BY id LIMIT ?}。
     */
    private static final int BATCH_SIZE = 200;

    /**
     * 单次任务的重算上限（防御性硬边界）。
     * <p>
     * 不设上限时，若菜品表异常膨胀，单次任务会长时间占用调度线程与连接池。
     * 触顶即记 WARN 并结束——<b>剩余部分留给下一次对账</b>：对账是幂等的，
     * 分几次跑完与一次跑完结果相同，只是修复延迟变长。
     */
    private static final int MAX_TOTAL = 20_000;

    /**
     * 每天 04:10 执行一次（避开 {@code EmailVerificationCodeCleanupTask} 的 03:00，错开数据库负载高峰）。
     */
    @Scheduled(cron = "0 10 4 * * ?")
    public void reconcileRatings() {
        try {
            int processed = 0;
            long lastId = 0L;
            while (processed < MAX_TOTAL) {
                List<Long> batch = dishMapper.selectDishIdsWithRatings(lastId, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (Long dishId : batch) {
                    // 幂等全量重算；单条失败（如该菜品刚被删除）不应中断整批
                    try {
                        dishMapper.recalcRatingBySubquery(dishId);
                    } catch (Exception e) {
                        log.warn("评分对账：单品重算失败 dishId={}，已跳过", dishId, e);
                    }
                }
                lastId = batch.get(batch.size() - 1);
                processed += batch.size();
            }
            if (processed > 0) {
                log.info("评分对账完成：已重算 {} 个菜品的评分聚合", processed);
            }
            if (processed >= MAX_TOTAL) {
                log.warn("[ALERT] 评分对账触达单次上限 {} 个菜品，剩余部分留待下一次对账（对账幂等，分批修复不影响结果）",
                        MAX_TOTAL);
            }
        } catch (Exception e) {
            log.error("[ALERT] 评分对账任务异常，本次跳过（下次对账会重新覆盖）", e);
        }
    }
}