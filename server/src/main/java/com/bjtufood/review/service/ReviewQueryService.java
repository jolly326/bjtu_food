package com.bjtufood.review.service;

import com.bjtufood.review.dto.StallAvgRatingVO;

import java.util.Collection;
import java.util.List;

/**
 * 评价只读查询端口（跨域读契约）。
 * <p>
 * 2026-09-27 架构收口 P0-1：canteen（档口列表平均分）此前直接 import
 * {@code review.mapper.ReviewMapper} + {@code review.dto.StallAvgRatingVO} 跨模块查库。
 * 现经本端口读取。
 * <p>
 * <b>为何不复用 {@link ReviewService}</b>：ReviewServiceImpl 依赖 DishService（管理端列表补全菜品名）
 * 与 UserService（昵称头像），而 dish/auth 侧又要反向触达 review 的清理能力（已改由领域事件解耦）。
 * 若把跨域投影方法挂进 ReviewService，canteen → ReviewService 的注入会把「查询用的轻依赖」
 * 升级成「重量级写服务依赖」，Spring 构造期依赖图随之放大（循环依赖风险面扩大）。
 * 故按读写职责拆分：本接口零业务依赖，可被任意模块安全注入。
 */
public interface ReviewQueryService {

    /**
     * 批量查询档口平均分（SQL 聚合 GROUP BY stall_id，调用方一次批量取回，禁止 N+1）。
     * <p>
     * 口径与评价列表页一致：{@code AVG(rating) WHERE status='approved'}，保留 2 位小数。
     *
     * @param stallIds 档口ID集合（null 或空集合返回空列表）
     * @return 平均分投影列表；<b>无任何 approved 评价的档口不会出现在结果集中</b>，调用方按需兜底 0
     */
    List<StallAvgRatingVO> findAvgRatingByStallIds(Collection<Long> stallIds);
}
