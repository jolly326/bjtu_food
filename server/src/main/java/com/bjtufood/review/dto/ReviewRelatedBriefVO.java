package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 关联评价摘要（**B3 举报列表内嵌「被举报内容」**用）。
 *
 * <p>为什么单独建这个小 VO 而不是复用 {@code ReviewAdminVO}：举报列表一页 10~20 行，
 * 只需要「内容 + 所属菜品 + 是否已隐藏」三项；复用管理端 VO 会把昵称、头像、图片绝对地址
 * 等一整套富化逻辑（含逐行跨域取数）都带上，是纯粹的浪费。
 *
 * <p>取数**必须批量**：逐行查询会退化成 N+1（一页 20 条 = 20 次查询加 20 次菜品名查询）。
 *
 * @see com.bjtufood.review.service.ReviewService#mapRelatedBriefByIds
 */
@Data
@Schema(description = "关联评价摘要（举报列表内嵌）")
public class ReviewRelatedBriefVO {

    /** 被举报评价正文（评价已被物理删除时为 null） */
    @Schema(description = "被举报评价正文")
    private String content;

    /** 被举报评价所属菜品名（联表带出；菜品已删除时为 null） */
    @Schema(description = "所属菜品名")
    private String dishName;

    /** 该评价当前是否已隐藏（决定管理端「同时隐藏」复选是否置灰） */
    @Schema(description = "该评价当前是否已隐藏")
    private boolean hidden;
}
