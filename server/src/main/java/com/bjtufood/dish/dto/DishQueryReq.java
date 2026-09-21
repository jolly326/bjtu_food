package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品列表查询参数。
 * <p>
 * 已下线参数（2026-09-20 拍板）：{@code tag}（标签整链删除）、
 * {@code spiceLevel}（辣度维度删除，学生端不再提供辣度筛选入口）。
 */
@Data
@Schema(description = "菜品列表查询参数")
public class DishQueryReq {

    @Schema(description = "页码，从1开始", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词，匹配菜品名/别名或档口名/食堂名", example = "牛肉")
    private String keyword;

    @Schema(description = "食堂ID筛选", example = "1")
    private Long canteenId;

    @Schema(description = "最低价格，单位：分", example = "1000")
    private Integer minPrice;

    @Schema(description = "最高价格，单位：分", example = "2000")
    private Integer maxPrice;

    /**
     * 菜品大类筛选（2026-09-21 §7.34）：单值，取值 = 大类枚举键
     * （set_meal / stir_fry / noodle / dry_pot / snack / soup_drink）。
     * 白名单校验在 Service 层完成，非法值 → 400（PR-06：不静默降级）。
     * 与 canteenId / keyword / 价格区间可叠加，且不改变排序口径（仍热度倒序）。
     */
    @Schema(description = "菜品大类筛选（单值）：set_meal 套餐盖饭 / stir_fry 家常小炒 / noodle 面食粉类 / dry_pot 香锅干锅 / snack 风味小吃 / soup_drink 汤饮甜品", example = "noodle")
    private String mealType;

}
