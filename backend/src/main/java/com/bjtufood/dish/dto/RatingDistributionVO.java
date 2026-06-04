package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评分分布视图对象（VO）
 * <p>
 * 菜品详情页各星级的人数统计
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评分分布")
public class RatingDistributionVO {

    @Schema(description = "星级（1-5）", example = "5")
    private Integer star;

    @Schema(description = "该星级人数", example = "156")
    private Long count;
}
