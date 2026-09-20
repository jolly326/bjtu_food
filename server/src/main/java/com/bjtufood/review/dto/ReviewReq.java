package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 评价提交 / 重新评价请求参数。
 * <p>
 * 不含菜品 ID：归属由端点路径表达
 * （{@code POST /dishes/{id}/reviews} 发表、{@code PUT /reviews/{id}} 重新评价）。
 */
@Data
@Schema(description = "评价提交/重新评价请求参数")
public class ReviewReq {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低是1")
    @Max(value = 5, message = "评分最高是5")
    @Schema(description = "评分，1-5星", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;

    @Size(max = 500, message = "评论内容不能超过500字")
    @Schema(description = "文字评价", example = "味道不错，分量也足。")
    private String content;

    @Size(max = 3, message = "评价配图最多 3 张")
    @Schema(description = "评价配图 URL 列表（经 POST /upload/images 转存的 COS 绝对地址，≤3 张）")
    private List<String> images;
}
