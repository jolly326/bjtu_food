package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索联想视图对象（VO）
 * <p>
 * 混合菜品 / 档口 / 食堂名的联想建议，前端按 type 跳转对应详情页。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "搜索联想建议")
public class SuggestionVO {

    @Schema(description = "类型：dish / stall / canteen", example = "dish")
    private String type;

    @Schema(description = "目标 ID（菜品 / 档口 / 食堂 ID）", example = "1")
    private Long id;

    @Schema(description = "名称", example = "牛肉拉面")
    private String name;

    @Schema(description = "封面 / 主图 URL", example = "/images/seed/dishes/beef-noodle.jpg")
    private String image;

    @Schema(description = "所属食堂名（仅 stall 类型返回，供跳档口详情携带 navParams.canteen）", example = "一食堂")
    private String canteen;
}
