package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 菜品详情视图对象（VO）
 * <p>
 * 继承 DishVO，增加详情页专属字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "菜品详情展示信息")
public class DishDetailVO extends DishVO {

    @Schema(description = "评分分布（1-5星各一个）")
    private List<RatingDistributionVO> ratingDistribution;
}
