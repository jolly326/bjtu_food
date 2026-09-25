package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品纠错管理端视图对象（{@code GET /admin/corrections}）。
 */
@Data
@Schema(description = "菜品纠错管理端展示信息")
public class DishCorrectionAdminVO {

    @Schema(description = "纠错ID")
    private Long id;

    @Schema(description = "目标菜品ID")
    private Long dishId;

    /**
     * 目标菜品名：联表回查 dish 实时补齐（菜品可能已被改名，<b>不区分上/下架</b>，含已下架）；
     * 菜品已被物理删除时为 null（前端按「菜品已删除」缺省展示）。
     */
    @Schema(description = "目标菜品名（实时回查 dish；菜品已物理删除为 null）")
    private String dishName;

    @Schema(description = "提交人用户ID（匿名提交为 null）")
    private Long userId;

    @Schema(description = "提交人昵称（匿名提交为 null）")
    private String userNickname;

    @Schema(description = "提交的菜品名称")
    private String name;

    @Schema(description = "提交的现价（分）", example = "1600")
    private Integer price;

    @Schema(description = "提交的食堂名称")
    private String canteenName;

    @Schema(description = "提交的档口名称")
    private String stallName;

    @Schema(description = "提交的口味标签（机器值数组）")
    private List<String> flavorTags;

    @Schema(description = "提交的主料/食材（机器值数组）")
    private List<String> ingredients;

    @Schema(description = "提交的菜品图片 URL 列表（COS 绝对地址）")
    private List<String> images;

    @Schema(description = "处理状态：pending/adopted/rejected")
    private String status;

    @Schema(description = "处理回复（采纳时固定「已采纳，菜品信息已更新」）")
    private String reply;

    @Schema(description = "不采纳原因（status=rejected 时非空）")
    private String rejectReason;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
