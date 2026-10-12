package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图对象（管理端专用 VO）
 * <p>
 * 管理端独有语义字段为 {@code hidden}（是否被隐藏，仅管理端可见/可改）与
 * {@code moderatedByName} / {@code moderatedAt}（最近一次可见性处置的留痕）；
 * 不下发内容安全态。
 */
@Data
@Schema(description = "评价展示信息（管理端专用，含可见性状态与处置留痕）")
public class ReviewAdminVO {

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "评价者用户ID")
    private Long userId;

    @Schema(description = "关联菜品ID")
    private Long dishId;

    @Schema(description = "关联菜品名称")
    private String dishName;

    @Schema(description = "评价者昵称", example = "张三")
    private String userName;

    @Schema(description = "评价者头像URL")
    private String userAvatar;

    @Schema(description = "评分（1-5星）")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价配图 URL 列表（COS 绝对地址，≤3 张）")
    private List<String> images;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;

    /**
     * 是否被隐藏（管理端用）。
     * <p>
     * B1 口径：出参为 **`hidden`(boolean)** ——
     * 端上是「显示中 / 已隐藏」**二态**，0/1 会逼消费方做真假转换或写魔法数字。
     * 库列 `review.is_hidden` 是 0/1，**只改出参、不改列**。
     */
    @Schema(description = "是否被隐藏（管理端用）")
    private Boolean hidden;

    /**
     * **隐藏附注**（最近一次隐藏时管理员填写的可选说明；恢复显示或从未隐藏为 {@code null}）。
     * <p>
     * 来源 = 库列 {@code review.hidden_note}，与 {@code moderatedByName} / {@code moderatedAt} 同生命周期、
     * 覆盖式（见 docs/schema/review.md）；出参供管理端复查「上一次为什么隐藏」。
     */
    @Schema(description = "隐藏附注（最近一次隐藏说明；未隐藏为 null）")
    private String hiddenNote;

    /**
     * **最近一次可见性处置的处置人**（管理员登录名；未处置为 {@code null}）。
     * <p>
     * 来源 = 库列 {@code review.moderated_by}（外键 → `admin.id`），登录名经 auth 域只读契约
     * {@code AdminAccountService#mapUsernameByIds} **批量**取回（review 域不直连 admin 表）。
     * 与 {@code hiddenNote} 同生命周期、**覆盖式**：隐藏与恢复显示都刷新，只反映最近一次处置。
     */
    @Schema(description = "最近一次可见性处置的处置人登录名（未处置为 null）")
    private String moderatedByName;

    /**
     * **最近一次可见性处置的时间**（未处置为 {@code null}）。
     * <p>
     * 与 `hiddenNote` / `moderatedByName` 同生命周期、覆盖式；**不是**「最后活动时间」——
     * 用户覆盖提交评价不会刷新它（库列由处置 UPDATE 以 DB 时钟写入，见 docs/schema/review.md）。
     */
    @Schema(description = "最近一次可见性处置的时间（未处置为 null）")
    private LocalDateTime moderatedAt;
}
