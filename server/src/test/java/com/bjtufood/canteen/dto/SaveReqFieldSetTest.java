package com.bjtufood.canteen.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 食堂 / 档口写入面的<b>字段集守卫</b>（口径 = docs/api/web/stalls.md 的「不接收字段」清单 +
 * 空值语义表）。
 *
 * <p><b>本类防的具体事故</b>：控制器若把请求体交回实体（或有人为了「顺手」把
 * {@code images} / {@code location} / {@code sortOrder} / {@code createdAt} 加回 DTO），
 * mass-assignment 面会<b>静默重开</b> —— 请求体里带上这些字段即可直写保留列与时间列，
 * 而编译、类型检查、接口冒烟<b>都不会报错</b>。字段集一旦与契约漂移，本条即变红。
 */
class SaveReqFieldSetTest {

    @Test
    @DisplayName("CanteenSaveReq 字段集 = {name}：保留列 / 时间列 / 派生统计不在写入面内")
    void canteenSaveReqExposesOnlyEditableField() {
        assertThat(CanteenSaveReq.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("name");
    }

    @Test
    @DisplayName("StallSaveReq 字段集 = {canteenId, name, floor, windowNo}：保留列 / 时间列 / 派生统计不在写入面内")
    void stallSaveReqExposesOnlyEditableFields() {
        assertThat(StallSaveReq.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("canteenId", "name", "floor", "windowNo");
    }
}
