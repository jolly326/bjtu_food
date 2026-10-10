package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数（**完整参数集恰为 5 项**：page / pageSize / keyword / view / seed）。
 * <p>
 * {@code view} 是<b>筛选视图 ID</b>（值域见 {@code GET /dishes/views}），<b>不是</b>菜品入库字段
 * {@code categoryId}：视图行的归属种类（{@code categoryId}，可空）决定取数是否按种类过滤，
 * 取数口径固定为「种类过滤（可空）+ 随机序 + 在售」，端上无须改动。
 * <p>
 * 不接受参数（SHALL NOT 回流）：
 * <ul>
 *   <li>{@code tag} —— 标签整链删除；</li>
 *   <li>{@code spiceLevel} —— 由描述维度承载；</li>
 *   <li>{@code stallId} —— 无档口筛选入口；</li>
 *   <li>{@code sortBy} / {@code sortOrder} —— 排序口径收敛为服务端决定：
 *       固定随机序，{@code seed} 只是数据顺序种子而非排序参数，端上无排序入口；</li>
 *   <li>{@code canteenId} / {@code minPrice} / {@code maxPrice} —— **食堂 / 价格筛选全量下线**；</li>
 *   <li>菜品分类 —— 由所选视图的归属种类表达，不作为独立查询参数。</li>
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

    @Schema(description = "筛选视图 ID（值域见 GET /dishes/views；白名单校验，非法值 400；空 = 首个启用视图）",
            example = "2")
    private Long view;

    @Schema(description = "推荐流会话随机种子（可选）：服务端按 CRC32(seed:ID) 稳定伪随机序取数，"
            + "同一次会话内多次进入拿到同一批内容；未传时服务端补一次性随机值（仍为随机序）", example = "m3k9x7q2")
    private String seed;
}
