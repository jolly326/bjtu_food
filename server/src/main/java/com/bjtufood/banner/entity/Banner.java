package com.bjtufood.banner.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页顶部 Banner 轮播图实体
 * <p>
 * 对应数据库表：banner（2026-09-22 新增，change「首页 Banner 接口化」）。
 * <ul>
 *   <li>{@code status} 取 {@code on} / {@code off}（与 {@code dish.status} 同风格；**不复活**已随下线删除的
 *       {@code enabled} / {@code disabled} 枚举）；服务端按 {@code status='on'} 过滤，该列不出参。</li>
 *   <li>{@code sort_order} 为展示顺序（升序），服务端排序用、不出参。</li>
 *   <li>{@code image_url} 与菜品图片同口径：库内可存相对路径，出参经 {@code ImageUrlUtil} 转绝对 URL。</li>
 * </ul>
 * <p>
 * 本期仅只读：管理端录入（/admin/banners）不落地，素材由 {@code seed_data.sql} 维护；需要运营自助录入时另立 change。
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

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
