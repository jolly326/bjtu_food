package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数（**完整参数集恰为 5 项**：page / pageSize / keyword / mealType / seed）。
 * <p>
 * 已下线参数（SHALL NOT 回流）：
 * <ul>
 *   <li>{@code tag} —— 标签整链删除（2026-09-20）；</li>
 *   <li>{@code spiceLevel} —— 由四维描述字段替换（§7.28）；</li>
 *   <li>{@code stallId} —— 无档口筛选入口（2026-09-21 §7.23 第 6 条）；</li>
 *   <li>{@code sortBy} / {@code sortOrder} —— 排序口径收敛为服务端决定（2026-09-21 §7.33；
 *       2026-09-27 起为双分支：推荐流按 {@code seed} 伪随机序、其余热度倒序——{@code seed}
 *       是数据顺序种子而非排序参数，端上仍无排序入口）；</li>
 *   <li>{@code canteenId} / {@code minPrice} / {@code maxPrice} —— **食堂 / 价格筛选全量下线（2026-09-22 K3）**：
 *       首页筛选面板与搜索页筛选胶囊一并删除后，三者全端零发送，服务端条件与 SQL 分支同批移除。</li>
 * </ul>
 */
@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码，从1开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数（服务端上限 100）", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词，匹配菜品名/别名或档口名/食堂名", example = "牛肉")
    private String keyword;

    @Schema(description = "菜品大类筛选（单值，键域=MealTypeConst；白名单校验，非法值 400）", example = "noodle")
    private String mealType;

    @Schema(description = "推荐流会话随机种子（可选；仅无 keyword/mealType 且非空时参与排序，"
            + "服务端按 CRC32(seed:ID) 稳定伪随机序；缺省=热度倒序）", example = "m3k9x7q2")
    private String seed;
}
