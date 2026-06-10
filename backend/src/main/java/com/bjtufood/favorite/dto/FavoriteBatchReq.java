package com.bjtufood.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量收藏请求参数")
public class FavoriteBatchReq {

    @NotEmpty(message = "dishIds 不能为空")
    @Schema(description = "菜品ID列表", example = "[1, 2, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> dishIds;
}
