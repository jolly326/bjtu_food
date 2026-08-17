package com.bjtufood.content.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品分类视图对象（VO）
 * <p>
 * find 页分类宫格渲染；图标由前端按 §0.5 映射表匹配，后端仅提供 id/name/排序。
 */
@Data
@Schema(description = "菜品分类展示信息")
public class CategoryVO {

    @Schema(description = "分类ID")
    private Long id;

    @Schema(description = "品类机器标识（唯一，前端滚轮 key 与筛选用）", example = "noodle")
    private String code;

    @Schema(description = "分类名称", example = "面食")
    private String name;

    @Schema(description = "排序权重（越小越靠前，对应首页品类滚轮顺序）")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
