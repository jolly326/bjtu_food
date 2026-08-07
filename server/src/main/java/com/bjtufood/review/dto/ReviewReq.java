package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "评价提交/修改请求参数")
public class ReviewReq {

    @NotNull(message = "菜品ID不能为空")
    @Schema(description = "菜品ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long dishId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低是1")
    @Max(value = 5, message = "评分最高是5")
    @Schema(description = "评分，1-5星", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;

    @Schema(description = "文字评价", example = "味道不错，分量也足。")
    private String content;

    @Schema(description = "评价图片 URL 列表，建议先调用 /upload/image 获取 URL", example = "[\"/images/seed/dishes/tomato-egg.jpg\"]")
    private List<String> images;

    @Schema(description = "是否同步为社区动态（评价可见即动态可见：同步生成的动态直接通过审核，无需后台审核；评价无内容时不生成）", example = "false")
    private Boolean shareToMoment;
}
