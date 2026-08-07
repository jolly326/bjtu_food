package com.bjtufood.moment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态「有用 👍」切换结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态有用切换结果")
public class MomentUsefulResult {

    @Schema(description = "当前用户是否已标记为有用", example = "true")
    private Boolean useful;

    @Schema(description = "有用标记总数", example = "12")
    private Integer usefulCount;
}
