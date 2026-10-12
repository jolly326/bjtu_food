package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 评价创建成功出参（POST /dishes/{id}/reviews）。
 * <p>
 * 首次提交返回**新评价 ID**——端上据此本地写回「我的评价」态，无须回读 {@code GET /my/reviews?dishId=}
 * （底栏文案恒为「写评价」，不设「重新评价」双态，见 docs/ui/client/菜品详情.md）。
 */
@Data
@Schema(description = "评价创建成功出参")
public class ReviewCreatedVO {

    @Schema(description = "新评价 ID（供本地写回）", example = "9")
    private Long id;

    public ReviewCreatedVO(Long id) {
        this.id = id;
    }
}
