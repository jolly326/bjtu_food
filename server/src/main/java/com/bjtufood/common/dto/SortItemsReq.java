package com.bjtufood.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 拖拽排序提交体（**整体替换**顺序）。
 *
 * <p>口径真源：docs/web/README.md 的「拖拽排序提交」通用约定 ——
 * `items` **必须是该列表的全量行**（缺行 ⇒ 顺序不完整 → `400`）；
 * `items` 为空 / 含未知 `id` / 含重复 `id` / 同一 `order` 重复 → 一律 `400`「排序提交非法」。
 *
 * <p>六个端点共用：A4 维度 / A4 取值 / A5 Banner / A6 视图 / A6 分类值 / A7 举报原因。
 */
@Data
@Schema(description = "排序提交体（全量行、整体替换）")
public class SortItemsReq {

    @Schema(description = "全量行（不得缺行、不得重复 id / order）")
    @NotEmpty(message = "排序提交非法")
    private List<SortItem> items;
}
