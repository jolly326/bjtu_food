package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评价「有用」切换结果
 * <p>
 * 调用 {@code POST /reviews/{id}/useful} 后返回，前端据此更新 UI 状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评价有用切换结果")
public class UsefulResult {

    @Schema(description = "当前用户是否已标记为「有用」", example = "true")
    private Boolean useful;

    @Schema(description = "「有用」标记总数", example = "12")
    private Integer usefulCount;
}
