package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 评价提交请求 DTO
 * <p>
 * POST /api/reviews 的请求体
 */
@Data
@Schema(description = "评价提交请求参数")
public class ReviewReq {

    @NotNull(message = "菜品ID不能为空")
    @Schema(description = "菜品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long dishId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    @Schema(description = "评分（1-5星）", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;

    @Schema(description = "文字评价", example = "味道不错，分量很足")
    private String content;

    @Schema(description = "评价图片URL列表（最多3张）")
    private List<String> images;
}
