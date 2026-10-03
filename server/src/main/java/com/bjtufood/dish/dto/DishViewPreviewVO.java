package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * A6 预览出参（**保存前试算，不入库**）。
 *
 * <p>`sampleNames` 是前若干条菜名 —— 管理员据此判断「条件写得对不对」
 * （只有数字没有样名，很难发现「分类键写错导致命中 0 条」这类问题）。
 */
@Data
@Schema(description = "视图条件预览出参")
public class DishViewPreviewVO {

    @Schema(description = "匹配的在售菜品数")
    private Long matchedCount;

    @Schema(description = "前若干条菜名（抽样）")
    private List<String> sampleNames;
}
