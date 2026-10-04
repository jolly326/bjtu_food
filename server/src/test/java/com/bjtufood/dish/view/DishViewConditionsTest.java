package com.bjtufood.dish.view;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.entity.Dish;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 视图条件引擎的**白名单护栏**单测（安全底线，口径见
 * docs/schema/dish_filter_view.md 的「条件语言是有限语言」与 docs/func/web/A-主数据维护/A6-首页筛选视图管理.md）。
 * <p>
 * 这里断言的是「**不能**做什么」：任何白名单外的字段 / 操作符 / 取值形态一律 `400`，
 * 且全部取值**参数化**绑定（不拼接进 SQL 文本）。
 */
class DishViewConditionsTest {

    /** MyBatis-Plus 的 lambda 缓存需显式初始化（QueryWrapper 走列名字符串，此处仍统一初始化以免顺序耦合） */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                DishViewConditionsTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Dish.class);
    }

    private static DishViewCondition condition(String field, String op, String value) {
        DishViewCondition c = new DishViewCondition();
        c.setField(field);
        c.setOp(op);
        c.setValue(value);
        return c;
    }

    @Test
    @DisplayName("白名单：字段不在表内 → 400（不可配 SQL / 表达式）")
    void unknownField_rejected400() {
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("1=1 --", "=", "x"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("白名单：操作符不属该字段 → 400（如 mealType 不支持 >=）")
    void illegalOpForField_rejected400() {
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("mealType", ">=", "noodle"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("取值形态：数字字段传非数字 → 400（防注入与脏条件）")
    void nonNumericValueForNumericField_rejected400() {
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("price", ">=", "1 OR 1=1"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("stallId", "=", "abc"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("createdAt", "withinDays", "0"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("取值缺失：需要取值的操作符未给值 → 400；between 必须恰两个")
    void missingValue_rejected400() {
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("price", ">=", "  "))))
                .isInstanceOf(BusinessException.class);
        DishViewCondition between = new DishViewCondition();
        between.setField("price");
        between.setOp("between");
        between.setValues(List.of("100"));
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(between)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("discount.isTrue 无值：合法且翻译为派生条件")
    void discountIsTrue_isValueless() {
        List<DishViewCondition> ok = DishViewConditions.validate(List.of(condition("discount", "isTrue", null)));

        QueryWrapper<Dish> wrapper = DishViewConditions.toWrapper(ok, true);
        // 在售过滤 + 派生的「原价 > 现价」
        assertThat(wrapper.getSqlSegment()).contains("status").contains("original_price > price");
    }

    @Test
    @DisplayName("翻译：mealType 等值 / in、canteenId 走子查询，且取值走参数占位符")
    void translate_toParameterizedWrapper() {
        DishViewCondition in = new DishViewCondition();
        in.setField("mealType");
        in.setOp("in");
        in.setValues(List.of("noodle", "snack"));

        QueryWrapper<Dish> wrapper = DishViewConditions.toWrapper(
                DishViewConditions.validate(List.of(condition("mealType", "=", "noodle"), in,
                        condition("canteenId", "=", "2"), condition("avgRating", ">=", "4"))), true);

        String sql = wrapper.getSqlSegment();
        // 取值以 #{ew.paramNameValuePairs.MPGENVAL*} 占位（**不拼接进 SQL 文本**）
        assertThat(sql).contains("meal_type").contains("IN").contains("avg_rating");
        assertThat(sql).contains("SELECT id FROM stall WHERE canteen_id = 2");
        assertThat(sql).doesNotContain("'noodle'").doesNotContain("noodle,");
    }

    @Test
    @DisplayName("JSON 往返：序列化后再解析等价；空条件 → 空列表（NULL / [] = 全部菜品）")
    void jsonRoundTripAndEmpty() {
        List<DishViewCondition> conditions =
                DishViewConditions.validate(List.of(condition("mealType", "=", "noodle")));

        String json = DishViewConditions.toJson(conditions);

        assertThat(DishViewConditions.parse(json)).hasSize(1);
        assertThat(DishViewConditions.toJson(List.of())).isNull();
        assertThat(DishViewConditions.parse(null)).isEmpty();
        assertThat(DishViewConditions.parse("[]")).isEmpty();
    }

    @Test
    @DisplayName("排序口径白名单：7 项齐全（含 priceAsc / ratingDesc / newest）")
    void sortKindWhitelist_isComplete() {
        assertThat(DishViewConditions.SORT_KINDS)
                .containsExactlyInAnyOrder("heat", "priceAsc", "priceDesc", "discountDesc",
                        "ratingDesc", "newest", "random");
    }
}
