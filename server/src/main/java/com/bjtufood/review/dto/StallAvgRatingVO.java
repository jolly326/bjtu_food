package com.bjtufood.review.dto;

import java.math.BigDecimal;

/**
 * 档口平均评分批量查询结果。
 * <p>
 * <b>归属说明</b>：本 VO 虽以「档口」为聚合维度（档口实体属
 * {@code canteen} 域），但它<b>不是</b> canteen 的读模型，而是
 * {@link com.bjtufood.review.service.ReviewQueryService} 的<b>出参</b>——
 * 聚合口径（rating 均值）完全由 review 表与评价逻辑定义，canteen 不参与计算。
 * 故留在 {@code review.dto}，消费方经 {@code ReviewQueryService} 取数
 * （而非直连 review 的 Mapper，符合 P0-1 跨域只走契约）。
 * <p>
 * ：原先由 {@code StallServiceImpl} 消费，构成
 * {@code canteen -> review -> dish -> canteen} 包级环；现改由
 * {@code CanteenAdminController#fillAvgRatings} 在编排层消费（canteen 业务层零 review 依赖）。
 * <p>
 * 跨域消费 VO 是 P0-1 有意允许的口子（跨域只传 DTO/投影，禁传实体），
 * 此处显式声明归属理由，避免后人误以为「档口概念就该放 canteen」而搬走。
 */
public class StallAvgRatingVO {

    /** 档口ID（dish.stall_id） */
    private Long stallId;

    /** 平均分（可能为 null：该档口下无评价） */
    private BigDecimal avgRating;

    public Long getStallId() {
        return stallId;
    }

    public void setStallId(Long stallId) {
        this.stallId = stallId;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }
}
