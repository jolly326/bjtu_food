package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页筛选视图（表 {@code dish_filter_view}）—— **tab 的全部口径都在本表**。
 * <p>
 * 一个 tab = 文案 + 启停 + **归属种类**（{@code categoryId}）：
 * <ul>
 *   <li>{@code categoryId} 为 {@code NULL} ⇒ 不按种类过滤（「为你推荐」等聚合视角）；
 *       非 {@code NULL} ⇒ 引用 {@code dish_category.id}，取数按该种类过滤；</li>
 *   <li>取数口径固定：{@code status='on'} + 种类过滤 + 随机序
 *       （{@code CRC32(CONCAT(seed,'-',id)), id} 会话稳定伪随机）；</li>
 *   <li>无「默认视图」概念（空 {@code view} 取首个启用视图）。</li>
 * </ul>
 * 口径真源：docs/schema/dish_filter_view.md 与 docs/api/web/views.md。
 */
@Data
@TableName("dish_filter_view")
@Schema(description = "首页筛选视图")
public class DishFilterView {

    @TableId(type = IdType.AUTO)
    @Schema(description = "视图ID（端上回传 view=<id>）")
    private Long id;

    @Schema(description = "tab 文案（可改）", example = "面食粉类")
    private String name;

    /** 归属种类（值域 = {@code dish_category.id}；NULL = 不按种类过滤） */
    @Schema(description = "归属种类ID（NULL = 不按种类过滤）", example = "3")
    private Long categoryId;

    @Schema(description = "是否在 client 首页出现（false=tab 隐藏）")
    private Boolean enabled;

    /** 更新时间（DB 时钟：`ON UPDATE CURRENT_TIMESTAMP`；应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
