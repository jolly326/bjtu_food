package com.bjtufood.review.dto;

import java.math.BigDecimal;

/**
 * 档口平均评分批量查询结果
 */
public class StallAvgRatingDTO {

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
