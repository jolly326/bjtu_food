package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「我的评价」视图对象（VO）—— **本人视角，11 字段**。
 * <p>
 * <b>2026-09-23 拆类</b>（§7.40 R9 / change {@code dish-detail-contract-hardening}）：
 * 此前公开列表与本人视角**共用 {@link ReviewVO}**，靠「调哪个接口」区分字段集 ——
 * 类型系统无法表达差异。现拆为两个类，字段集由**类型**表达：
 * 本人视角 = 公开视角 8 字段 **+** 本类新增 3 字段。
 * <p>
 * <b>继承而非复制</b>：本人视角在语义上就是「公开视角 + 作者视角 3 字段」，故 extends
 * {@link ReviewVO}（公开字段只定义一次）。消费端（小程序 / 管理端）**MUST 各定义两个类型**，
 * SHALL NOT 用单一 interface 复用（详见 {@code docs/feature/client-我的评价.md}）。
 * <p>
 * 新增 3 字段（仅 {@code GET /my/reviews} 返回）：
 * {@code dishId}（关联菜品，供详情页预填定位）、{@code dishName}（联表补齐，列表行展示）、
 * {@code isHidden}（本人可见被隐藏的评价，据此标注「已被隐藏」）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "我的评价展示信息（本人视角，11 字段 = 公开 8 + dishId / dishName / isHidden）")
public class MyReviewVO extends ReviewVO {

    /** 关联菜品ID（本人视角专属；公开列表不返回 —— 归属已由路径 / 列表上下文表达） */
    @Schema(description = "关联菜品ID（仅「我的评价」返回）")
    private Long dishId;

    /** 关联菜品名称（本人视角专属，mapper 联表 dish 补齐，供列表行展示） */
    @Schema(description = "关联菜品名称（仅「我的评价」返回，mapper 联表补齐）")
    private String dishName;

    /**
     * 是否被管理员隐藏（0=正常 / 1=已被隐藏）。
     * <p>
     * 仅本人视角返回：本人视角不过滤 {@code is_hidden}，被隐藏的评价作者仍可见，
     * 端上据此标注「已被隐藏」。公开列表已在 SQL 层过滤 {@code is_hidden=0}，
     * 且归属由路径表达，不对外暴露该语义。
     */
    @Schema(description = "是否被管理员隐藏：0=正常/1=已隐藏（仅「我的评价」本人视角返回）", example = "0")
    private Integer isHidden;
}
