package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 评价创建成功出参（POST /dishes/{id}/reviews）。
 * <p>
 * 首次提交返回**新评价 ID**——端上据此本地写回「我的评价」态（底栏就地切为「重新评价」），
 * 无须回读 {@code GET /my/reviews?dishId=}。
 */
@Data
@Schema(description = "评价创建成功出参")
public class ReviewCreatedVO {

    @Schema(description = "新评价 ID（供重新评价 PUT /reviews/{id} 与本地写回）", example = "9")
    private Long id;

    public ReviewCreatedVO(Long id) {
        this.id = id;
    }
}
