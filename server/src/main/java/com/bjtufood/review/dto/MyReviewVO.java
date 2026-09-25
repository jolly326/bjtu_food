package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「我的评价」视图对象（VO）—— **本人视角，10 字段**。
 * <p>
 * <b>拆类</b>（§7.40 R9 / change {@code dish-detail-contract-hardening}）：
 * 公开列表与本人视角**不共用 {@link ReviewVO}**，字段集由**类型**表达：
 * 本人视角 = 公开视角 8 字段 **+** 本类新增 2 字段。
 * <p>
 * <b>继承而非复制</b>：本人视角在语义上就是「公开视角 + 作者视角 2 字段」，故 extends
 * {@link ReviewVO}（公开字段只定义一次）。消费端（小程序 / 管理端）**MUST 各定义两个类型**，
 * SHALL NOT 用单一 interface 复用（详见 {@code docs/feature/client-我的评价.md}）。
 * <p>
 * 新增 2 字段（仅 {@code GET /my/reviews} 返回）：
 * {@code dishId}（关联菜品，供详情页预填定位）、{@code dishName}（联表补齐，列表行展示）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "我的评价展示信息（本人视角，10 字段 = 公开 8 + dishId / dishName）")
public class MyReviewVO extends ReviewVO {

    /** 关联菜品ID（本人视角专属；公开列表不返回 —— 归属已由路径 / 列表上下文表达） */
    @Schema(description = "关联菜品ID（仅「我的评价」返回）")
    private Long dishId;

    /** 关联菜品名称（本人视角专属，mapper 联表 dish 补齐，供列表行展示） */
    @Schema(description = "关联菜品名称（仅「我的评价」返回，mapper 联表补齐）")
    private String dishName;
}
