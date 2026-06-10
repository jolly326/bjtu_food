package com.bjtufood.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "收藏切换请求参数")
public class FavoriteToggleReq {

    @NotNull(message = "dishId 不能为空")
    @Schema(description = "要收藏或取消收藏的菜品ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long dishId;
}
