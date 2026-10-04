package com.bjtufood.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端 Banner 出参（A5）。
 *
 * <p>与公开 VO（{@link BannerVO}）的差异：管理端需要 `order` / `status` / 时间列以支撑列表与启停；
 * **图片地址出参一律转绝对 URL**（与公开端点同口径）。
 * 契约真源：docs/api/web/banners.md。
 */
@Data
@Schema(description = "管理端 Banner 出参")
public class BannerAdminVO {

    @Schema(description = "Banner ID")
    private Long id;

    @Schema(description = "轮播图地址（出参已转绝对 URL）")
    private String imageUrl;

    @Schema(description = "展示顺序（升序；列名 sort_order，出参为 order）")
    private Integer order;

    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
