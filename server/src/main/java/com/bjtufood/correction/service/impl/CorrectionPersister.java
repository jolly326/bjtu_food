package com.bjtufood.correction.service.impl;

import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 反馈落库（**最小化事务边界**：先机审、不占连接，再落库、才开事务）。
 * <p>
 * <b>为何独立成 Bean</b>：{@code submit} 的机审是一次外部 HTTP 外呼（微信内容安全检测，超时 5s）。
 * 若事务从方法入口就开始，期间数据库连接被持续占用，而 HikariCP 默认池仅 10 条，并发稍高即被占满
 * 并拖垮只读请求（连接池雪崩）。本类持有唯一的 {@code @Transactional}，主 Service 的 {@code submit}
 * 不标注事务 —— 机审在事务外完成，落库才开事务。
 * <p>
 * <b>为何必须是独立 Bean 而不是同类 private 方法</b>：Spring 声明式事务靠<b>代理</b>生效，
 * 同一类内自调用（{@code this.insert(...)}）<b>不经过代理 ⇒ 事务根本不会开启</b>。
 * <p>
 * 仅 {@code submit} 需要此处理：{@code adopt} / {@code reject} 不含外部 HTTP 调用，
 * 事务边界保持原样（写回 dish 与归档反馈必须原子）。
 */
@Component
@RequiredArgsConstructor
public class CorrectionPersister {

    private final DishCorrectionMapper correctionMapper;

    /**
     * 落库一条纠错（事务边界仅包住这一次 insert）。
     *
     * @param correction 已通过全部字段校验与机审的记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(DishCorrection correction) {
        correctionMapper.insert(correction);
    }
}
