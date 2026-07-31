package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热搜词视图对象（VO）
 * <p>
 * 一期限定：无真实搜索词埋点表，一期语义为「基于菜品热度的热门词条派生」，
 * 取综合热度最高的菜品名作为热搜词条，heat 为该词条的派生热度分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热搜词条")
public class HotSearchVO {

    @Schema(description = "热搜词（菜品名）", example = "牛肉拉面")
    private String keyword;

    @Schema(description = "热度分", example = "320")
    private Long heat;
}
