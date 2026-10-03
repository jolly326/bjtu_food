package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 视图筛选条件的一项（`{ field, op, value }`）—— **有限语言**的一元。
 *
 * <p>契约真源：docs/web/A-主数据维护/A6-首页筛选视图管理.md 的「筛选字段白名单」。
 * <b>字段 / 操作符 / 值三层白名单</b>：后台不可写 SQL / 表达式 / 函数，全部经
 * {@code DishViewConditions} 校验后**参数化**绑定。
 *
 * <p>取值形态：单值用 {@link #value}（如 `price >= 1000`、`avgRating >= 4`）；
 * 多值用 {@link #values}（`in`）；`discount.isTrue` **无值**。
 */
@Data
@Schema(description = "视图筛选条件项")
public class DishViewCondition {

    @Schema(description = "条件字段（白名单：mealType/discount/price/stallId/canteenId/avgRating/createdAt）",
            example = "mealType")
    private String field;

    @Schema(description = "操作符（白名单，随字段而定：= / in / isTrue / between / >= / <= / withinDays）",
            example = "=")
    private String op;

    @Schema(description = "单值（`= / >= / <= / withinDays` 用；between 时为下界）", example = "noodle")
    private String value;

    @Schema(description = "多值（`in` 用；`between` 时为 [下界, 上界]）")
    private List<String> values;
}
