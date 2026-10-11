package com.bjtufood.moderation.service;

import com.bjtufood.moderation.dto.ModerationTaskVO;

import java.util.Collection;
import java.util.Map;

/**
 * 管理端处置任务服务（三类工单共用的处置状态机）。
 *
 * <p><b>职责边界</b>：本服务只认「工单类型 + 工单 ID + 处置结论 + 处置文案」，
 * <b>不读</b>任何工单表（feedback / report / correction 属各自域的表知识）——
 * 故收件人（工单提交人）由工单属主域在调用时下发，本域据此投递站内回执快照。
 */
public interface ModerationTaskService {

    /**
     * 预建处置任务行（{@code status=pending}）。
     * <p>
     * 🔴 <b>必须与工单行的 INSERT 处于同一事务</b>：调用点 = 各工单的落库 Bean
     * （{@code FeedbackPersister} / {@code CorrectionPersister}）或 {@code @Transactional} 提交方法
     * —— 任一步失败一并回滚，杜绝「有工单无任务」（管理端列表漏行）或「有任务无工单」。
     * <p>
     * 时间戳取 DB 时钟（`created_at` = `DEFAULT CURRENT_TIMESTAMP`，与工单行同一事务写入），
     * 故无需（也不得）由应用层传入 —— 见 docs/schema/README.md〈时间戳写入来源〉。
     *
     * @param type   工单类型（{@code ModerationConst.TYPE_*}）
     * @param typeId 工单 ID（该工单表主键）
     */
    void create(String type, Long typeId);

    /**
     * 处置：更新任务行 + 投递站内回执快照（**同一事务**）。
     * <p>
     * 校验口径（非法一律 400，不静默降级）：
     * <ul>
     *   <li>{@code outcome} 必须命中 {@code ModerationConst.OUTCOMES}（缺省 handled）；</li>
     *   <li>{@code outcome=rejected} 时处置文案（{@code replyContent}）**必填**、纯空白 → 400
     *       —— 不采纳必须写清原因（该文案即回执正文，也是管理端回显的处置文案）；</li>
     *   <li>回执标题 / 正文留空时按（类型 + 结论）补缺省文案，保证回执**始终可读**；</li>
     *   <li>标题 ≤40 字、正文 ≤600 字。</li>
     * </ul>
     * 处置状态：任务行不存在 → 4001；已处置（{@code status != pending}）再处置 → 400
     * （防处置结论被静默改写 + 回执重复投递）。
     *
     * @param type            工单类型（{@code ModerationConst.TYPE_*}）
     * @param typeId          工单 ID
     * @param outcome         处置结论（{@code null}/空白按 handled）
     * @param replyTitle      回执短标题（可空 → 缺省文案）
     * @param replyContent    处置文案（{@code outcome=rejected} 时必填）
     * @param adminId         处置人账号 ID（写入 {@code handled_by}）
     * @param recipientUserId 收件人（工单提交人；为 null 不投递 —— 防御性兜底，提交入口恒要求登录态）
     */
    void handle(String type, Long typeId, String outcome, String replyTitle, String replyContent,
                Long adminId, Long recipientUserId);

    /**
     * 取处置任务当前结果（工单属主域的**前置判据**，只读）。
     * <p>
     * 用途：处置会带副作用时（举报「联动隐藏评价」/ 纠错「写回菜品」）须<b>先</b>确认记录仍待处置，
     * 否则「已处置后再提交」会先执行副作用、再被 {@link #handle} 判 400，留下未归档的副作用。
     * 前置判据只做短路，**真正的防重复结论仍由 {@link #handle} 在事务内强制**。
     *
     * @param type   工单类型
     * @param typeId 工单 ID
     * @return 处置结果投影；任务行缺失（数据不一致）返回 {@code null}
     */
    ModerationTaskVO findByTypeAndId(String type, Long typeId);

    /**
     * 批量回填处置结果（管理端列表用；一次 IN 查询，消除 N+1）。
     *
     * @param type    工单类型
     * @param typeIds 工单 ID 集合（null / 空集合返回空 Map，不发起查询）
     * @return 工单 ID → 处置结果投影（缺失的 ID 不入图）
     */
    Map<Long, ModerationTaskVO> mapByTypeAndIds(String type, Collection<Long> typeIds);

    /**
     * 待处置数（D1 看板）：{@code moderation_task} **单表 COUNT**（按类型），
     * 不走工单表的列表查询 —— 口径与各列表页一致（同一行数据的处置状态）。
     *
     * @param type 工单类型
     * @return 待处置条数
     */
    long countPending(String type);
}
