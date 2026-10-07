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
 * docs/schema/dish_filter_view.md 的「条件语言是有限语言」与
 * docs/api/web/views.md 的「筛选条件语言」）。
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
        // 种类条件只认取值 ID 字段；旧式「分类键」字段名不是白名单成员
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("mealType", "=", "noodle"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("种类条件：取值为数值（系统维度取值 ID），直接等值匹配 dish.meal_type_id")
    void mealTypeIdTranslatesToColumn() {
        QueryWrapper<Dish> wrapper = DishViewConditions.toWrapper(
                DishViewConditions.validate(List.of(condition("mealTypeId", "=", "7"))), true);

        assertThat(wrapper.getSqlSegment()).contains("meal_type_id");
        // 取值不走 SQL 文本拼接（参数占位符形态：形参映射）
        assertThat(wrapper.getParamNameValuePairs()).containsValue(7L);
    }

    @Test
    @DisplayName("白名单：操作符不属该字段 → 400（如 mealTypeId 不支持 >=）")
    void illegalOpForField_rejected400() {
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("mealTypeId", ">=", "7"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("discount", "=", "true"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("取值形态：数字字段传非数字 → 400（防注入与脏条件）")
    void nonNumericValueForNumericField_rejected400() {
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("price", ">=", "1 OR 1=1"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("stallId", "=", "abc"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DishViewConditions.validate(List.of(condition("mealTypeId", "=", "noodle"))))
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
    @DisplayName("翻译：种类 / 档口走参数占位符，canteenId 走子查询，其余取值参数化")
    void translate_toParameterizedWrapper() {
        DishViewCondition in = new DishViewCondition();
        in.setField("mealTypeId");
        in.setOp("in");
        in.setValues(List.of("7", "9"));

        QueryWrapper<Dish> wrapper = DishViewConditions.toWrapper(
                DishViewConditions.validate(List.of(condition("mealTypeId", "=", "7"), in,
                        condition("canteenId", "=", "2"), condition("avgRating", ">=", "4"))), true);

        String sql = wrapper.getSqlSegment();
        assertThat(sql).contains("meal_type_id").contains("avg_rating");
        // canteenId 经 stall 子查询（避免为计数引入联表）
        assertThat(sql).contains("SELECT id FROM stall WHERE canteen_id = 2");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(7L).containsValue(9L);
    }

    @Test
    @DisplayName("JSON 往返：序列化后再解析等价；空条件 → 空列表，序列化为 []（列 NOT NULL）")
    void jsonRoundTripAndEmpty() {
        List<DishViewCondition> conditions =
                DishViewConditions.validate(List.of(condition("mealTypeId", "=", "7")));

        String json = DishViewConditions.toJson(conditions);

        assertThat(DishViewConditions.parse(json)).hasSize(1);
        assertThat(DishViewConditions.toJson(List.of())).isEqualTo("[]");
        assertThat(DishViewConditions.parse(null)).isEmpty();
        assertThat(DishViewConditions.parse("[]")).isEmpty();
        // 非法 JSON / 白名单外字段：一律 400，不静默降级成「不筛选」
        assertThatThrownBy(() -> DishViewConditions.parse("不是 JSON"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DishViewConditions.parse("[{\"field\":\"1=1 --\",\"op\":\"=\"}]"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("排序口径白名单：6 项齐全（无 heat —— 浏览量无上限，不宜参与排序）")
    void sortKindWhitelist_isComplete() {
        assertThat(DishViewConditions.SORT_KINDS)
                .containsExactlyInAnyOrder("priceAsc", "priceDesc", "discountDesc",
                        "ratingDesc", "newest", "random")
                .doesNotContain("heat");
        assertThat(DishViewConditions.requireSortKind(" random ")).isEqualTo("random");
        assertThatThrownBy(() -> DishViewConditions.requireSortKind("heat"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }
}
