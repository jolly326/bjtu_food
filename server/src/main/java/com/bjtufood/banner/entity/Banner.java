package com.bjtufood.banner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页顶部 Banner 轮播图实体
 * <p>
 * 对应数据库表：banner。
 * <ul>
 *   <li>{@code status} 取 {@code on} / {@code off}（与 {@code dish.status} 同风格；**不复活**被删除的
 *       {@code enabled} / {@code disabled} 枚举）；服务端按 {@code status='on'} 过滤，该列不出参。</li>
 *   <li>{@code sort_order} 为展示顺序（升序），服务端排序用、不出参。</li>
 *   <li>{@code image_url} 与菜品图片同口径：库内可存相对路径，出参经 {@code ImageUrlUtil} 转绝对 URL。</li>
 * </ul>
 * <p>
 * 管理端维护入口：{@code /admin/banners}（列表 / 新增 / 换图 / 启停 / 排序 / 删除；启用上限 6 张）。
 */
@Data
@TableName("banner")
@Schema(description = "首页顶部轮播图")
public class Banner {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Banner ID")
    private Long id;

    /** 轮播图 URL（库内可存相对路径，出参转绝对 URL；素材统一 16:10） */
    @Schema(description = "轮播图URL（16:10 素材）")
    private String imageUrl;

    /** 展示顺序（升序，数字越小越靠前） */
    @Schema(description = "展示顺序（升序）")
    private Integer sortOrder;

    /** 状态：on=启用 / off=停用（同 dish.status 风格；服务端过滤用，不出参） */
    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
