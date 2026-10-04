package com.bjtufood.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 拖拽排序提交的**单行**（`{ id, order }`）。
 *
 * <p>口径真源：docs/api/README.md 的「拖拽排序提交」通用约定 ——
 * A4 维度 / A4 取值 / A5 Banner / A6 视图 / A6 分类值 / A7 举报原因 六个 `PUT .../sort` 共用本结构。
 */
@Data
@Schema(description = "排序提交的单行")
public class SortItem {

    @Schema(description = "目标行 ID", example = "3")
    private Long id;

    @Schema(description = "目标顺序（1 起、连续、互不相同）", example = "1")
    private Integer order;
}
