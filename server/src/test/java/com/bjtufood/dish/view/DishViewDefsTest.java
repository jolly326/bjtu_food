package com.bjtufood.dish.view;

import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.entity.DishFilterView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DishViewDefs}（视图逻辑真源）的等价性与边界护栏。
 * <p>
 * 断言两件事：① 7 个内置视图的条件 / 排序与基线**逐项等价**；② `key` 无定义时解析返回
 * {@code null}（调用方按 `400` 处理，见 {@code DishServiceImpl#listDishes}）。
 */
class DishViewDefsTest {

    private static DishFilterView row(String key) {
        DishFilterView v = new DishFilterView();
        v.setId(1L);
        v.setKey(key);
        v.setLabel(key);
        v.setOrder(1);
        v.setEnabled(true);
        return v;
    }

    @Test
    @DisplayName("7 个内置视图：key → 条件 / 排序逐项等价")
    void builtinDefs_areEquivalentToSpec() {
        Map<String, String> mealTypeViews = Map.of(
                "set_meal", "set_meal",
                "stir_fry", "stir_fry",
                "noodle", "noodle",
                "dry_pot", "dry_pot",
                "snack", "snack",
                "soup_drink", "soup_drink");

        mealTypeViews.forEach((key, value) -> {
            DishViewDefs.Def def = DishViewDefs.byKey(key);
            assertThat(def).as("key=%s", key).isNotNull();
            assertThat(def.sortKind()).as("key=%s", key).isEqualTo("heat");
            assertThat(def.conditions()).as("key=%s", key).hasSize(1);
            DishViewCondition c = def.conditions().get(0);
            assertThat(c.getField()).isEqualTo("mealType");
            assertThat(c.getOp()).isEqualTo("=");
            assertThat(c.getValue()).isEqualTo(value);
        });

        DishViewDefs.Def recommend = DishViewDefs.byKey("recommend");
        assertThat(recommend).isNotNull();
        assertThat(recommend.conditions()).isEmpty();
        assertThat(recommend.sortKind()).isEqualTo("random");
    }

    @Test
    @DisplayName("解析：random 视图带 seed，heat 视图不带 seed")
    void resolve_seedOnlyOnRandomView() {
        DishListQuery random = DishViewResolver.resolve(row("recommend"), null, "seed-1");
        assertThat(random).isNotNull();
        assertThat(random.sortKind()).isEqualTo(DishListQuery.SortKind.SEED_RANDOM);
        assertThat(random.seed()).isEqualTo("seed-1");
        assertThat(random.conditions()).isEmpty();

        DishListQuery heat = DishViewResolver.resolve(row("noodle"), null, "seed-1");
        assertThat(heat).isNotNull();
        assertThat(heat.sortKind()).isEqualTo(DishListQuery.SortKind.HEAT);
        assertThat(heat.seed()).isNull();
        assertThat(heat.conditions()).extracting(DishViewCondition::getValue).containsExactly("noodle");
    }

    @Test
    @DisplayName("边界：key 在 DishViewDefs 无定义 → resolve 返回 null（Service 抛 400）")
    void unknownKey_resolveNull() {
        assertThat(DishViewDefs.contains("ghost")).isFalse();
        assertThat(DishViewResolver.resolve(row("ghost"), null, null)).isNull();
        assertThat(DishViewResolver.resolve(null, null, null)).isNull();
    }
}
