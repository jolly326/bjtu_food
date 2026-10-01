package com.bjtufood.correction.service.impl;

import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 纠错落库（**最小化事务边界**）。
 * <p>
 * 背景：
 * 此前 {@code CorrectionServiceImpl#submit} 直接标注 {@code @Transactional} —— 事务从**方法入口**就开始，
 * 横跨「微信内容安全检测」这一次外部 HTTP 外呼（超时 5s）。期间数据库连接被持续占用，
 * 而 HikariCP 默认池仅 10 条，并发稍高即被占满并拖垮只读请求（连接池雪崩）。
 * <p>
 * 修法：<b>先机审（无事务、不占连接）→ 再落库（才开事务）</b>。为此把落库收敛到本类，
 * 由它持有唯一的 {@code @Transactional}；主 Service 的 {@code submit} 不再标注事务。
 * <p>
 * <b>为何必须是独立 Bean 而不是本类的 private 方法</b>：Spring 声明式事务靠<b>代理</b>生效，
 * 同一类内自调用（{@code this.insert(...)}）<b>不经过代理 ⇒ 事务根本不会开启</b>。
 * <p>
 * 仅 {@code submit} 需要此处理：{@code adopt} / {@code reject} 不含外部 HTTP 调用，
 * 事务边界保持原样（写回 dish 与归档纠错必须原子）。
 * <p>
 * 可见行为不变：落库字段值、错误码、失败语义均与原实现一致
 * （由 {@code CorrectionServiceImplTest} 逐条断言）。
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
