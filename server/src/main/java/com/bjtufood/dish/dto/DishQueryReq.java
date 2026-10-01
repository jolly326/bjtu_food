package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数（**完整参数集恰为 5 项**：page / pageSize / keyword / view / seed）。
 * <p>
 * {@code view} 是<b>筛选视图键</b>（值域见 {@code GET /dishes/views}），<b>不是</b>菜品入库字段
 * {@code mealType}：物理大类只是视图的一种（按 {@code meal_type} 取数），将来「折扣」等视图按别的
 * 口径取数，端上无须改动（详见 {@code com.bjtufood.dish.view.DishViewConst}）。
 * <p>
 * 已下线参数（SHALL NOT 回流）：
 * <ul>
 *   <li>{@code tag} —— 标签整链删除；</li>
 *   <li>{@code spiceLevel} —— 由四维描述字段替换（§7.28）；</li>
 *   <li>{@code stallId} —— 无档口筛选入口；</li>
 *   <li>{@code sortBy} / {@code sortOrder} —— 排序口径收敛为服务端决定（§7.33；
 *       起：推荐视图按 {@code seed} 伪随机序、其余按各视图自身口径——{@code seed}
 *       是数据顺序种子而非排序参数，端上仍无排序入口）；</li>
 *   <li>{@code canteenId} / {@code minPrice} / {@code maxPrice} —— **食堂 / 价格筛选全量下线**：</li>
 *   <li>{@code mealType} —— **更名为 {@code view}**（筛选是「用户想看什么」的视角，
 *       而非「菜品是什么」的字段；否则折扣等非食物类型视图无处安放）。</li>
 * </ul>
 */
@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码，从1开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数（服务端上限 100）", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词，匹配菜品名 / 档口名 / 食堂名（三处模糊匹配）", example = "牛肉")
    private String keyword;

    @Schema(description = "筛选视图键（值域见 GET /dishes/views；白名单校验，非法值 400；空 = 默认视图「为你推荐」）",
            example = "noodle")
    private String view;

    @Schema(description = "推荐流会话随机种子（可选；仅「推荐」类视图（sortKind=SEED_RANDOM）且无 keyword 时参与排序，"
            + "服务端按 CRC32(seed:ID) 稳定伪随机序；其余视图忽略本参数、按各自排序口径）", example = "m3k9x7q2")
    private String seed;
}
