package com.bjtufood.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.moderation.entity.ModerationTask;

/**
 * 管理端处置任务 Mapper。
 * <p>
 * 基础 CRUD 由 {@link BaseMapper} 提供；三处消费口径均落在单表上：
 * <ul>
 *   <li><b>提交预建</b>（{@code insert}，与工单行同一事务）；</li>
 *   <li><b>处置更新</b>（{@code updateById}，与站内回执同一事务）；</li>
 *   <li><b>看板待处置数</b>（{@code COUNT}，按 {@code type + status} 单表统计）。</li>
 * </ul>
 * 管理端列表的「批量回填」由 Service 按 {@code type_id} 集合一次 IN 查询（消除 N+1），
 * 无需自定义 XML。
 */
public interface ModerationTaskMapper extends BaseMapper<ModerationTask> {
}
