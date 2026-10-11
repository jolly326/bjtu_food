package com.bjtufood.correction.service.impl;

import com.bjtufood.correction.entity.Correction;
import com.bjtufood.correction.mapper.CorrectionMapper;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.service.ModerationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 纠错落库（**最小化事务边界**：先机审、不占连接，再落库、才开事务）。
 * <p>
 * <b>为何独立成 Bean</b>：{@code submit} 的机审是一次外部 HTTP 外呼（微信内容安全检测，超时 5s）。
 * 若事务从方法入口就开始，期间数据库连接被持续占用，而 HikariCP 默认池仅 10 条，并发稍高即被占满
 * 并拖垮只读请求（连接池雪崩）。本类持有唯一的 {@code @Transactional}，主 Service 的 {@code submit}
 * 不标注事务 —— 机审在事务外完成，落库才开事务。
 * <p>
 * 🔴 <b>事务内写两行</b>：纠错行 + 处置任务行（{@code status=pending}）。
 * 处置路径（{@code adopt} / {@code reject}）不含外部 HTTP 调用，也不写提交行（append-only），
 * 其事务边界由处置任务服务持有。
 * <p>
 * <b>为何必须是独立 Bean 而不是同类 private 方法</b>：Spring 声明式事务靠<b>代理</b>生效，
 * 同一类内自调用（{@code this.insert(...)}）<b>不经过代理 ⇒ 事务根本不会开启</b>。
 */
@Component
@RequiredArgsConstructor
public class CorrectionPersister {

    private final CorrectionMapper correctionMapper;
    /** 处置任务写入契约（同一事务内预建：见类注释） */
    private final ModerationTaskService moderationTaskService;

    /**
     * 落库一条纠错（事务边界包住「纠错行 + 处置任务行」两次 insert）。
     *
     * @param correction 已通过全部字段校验与机审的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(Correction correction) {
        correctionMapper.insert(correction);
        moderationTaskService.create(ModerationConst.TYPE_CORRECTION, correction.getId());
    }
}
