package com.bjtufood.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 首页轮播图视图对象（VO）—— **公开出参仅 2 个字段**
 * <p>
 * 契约口径（2026-09-22，见 docs/feature/client-首页菜品浏览.md I6）：
 * <ul>
 *   <li>{@code id}：轮播项稳定 key（端上 swiper 列表 key）；</li>
 *   <li>{@code imageUrl}：可直接渲染的**绝对 URL**（服务端经 ImageUtil 转换，绝对地址原样返回）；</li>
 *   <li>{@code sort_order} / {@code status} 为服务端过滤与排序用，端上零消费 → **不出参**；</li>
 *   <li>v1 无跳转能力 → **不出参** targetType / targetId / targetUrl（要支持跳转须先由 UI 文档定义交互再扩字段）。</li>
 * </ul>
 */
@Data
@Schema(description = "首页轮播图（公开 2 字段）")
public class BannerVO {

    @Schema(description = "Banner ID（轮播项 key）")
    private Long id;

    @Schema(description = "轮播图绝对 URL（16:10 素材）")
    private String imageUrl;
}
