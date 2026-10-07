package com.bjtufood.canteen.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 食堂 / 档口写入面的<b>字段集守卫</b>（口径 = docs/api/web/stalls.md 的「不接收字段」清单 +
 * 空值语义表）。
 *
 * <p><b>本类防的具体事故</b>：控制器若把请求体交回实体（或有人为了「顺手」把
 * {@code updatedAt} / {@code stallCount} / {@code dishCount} 加回 DTO），
 * mass-assignment 面会<b>静默重开</b> —— 请求体里带上这些字段即可直写时间列与派生统计，
 * 而编译、类型检查、接口冒烟<b>都不会报错</b>。字段集一旦与契约漂移，本条即变红。
 *
 * <p>时间列尤其危险：一旦可写，客户端可把 {@code updated_at} 显式写成 {@code null}
 * （{@code Column 'updated_at' cannot be null}）或写脏值，破坏「时间戳唯一来源 = DB 时钟」
 * 的约定（见 docs/schema/README.md）。
 */
class SaveReqFieldSetTest {

    @Test
    @DisplayName("CanteenSaveReq 字段集 = {name, location, description, images, sortOrder}：时间列 / 派生统计不在写入面内")
    void canteenSaveReqExposesOnlyEditableFields() {
        assertThat(instanceFieldNames(CanteenSaveReq.class))
                .containsExactlyInAnyOrder("name", "location", "description", "images", "sortOrder");
    }

    @Test
    @DisplayName("StallSaveReq 字段集 = {canteenId, name, floor, windowNo, location, description, images, sortOrder}")
    void stallSaveReqExposesOnlyEditableFields() {
        assertThat(instanceFieldNames(StallSaveReq.class))
                .containsExactlyInAnyOrder("canteenId", "name", "floor", "windowNo",
                        "location", "description", "images", "sortOrder");
    }

    /**
     * 写入面 = **实例字段**；`static final` 常量（如 {@code IMAGE_MAX}）是校验阈值，不属请求体字段，
     * 不能算进「可从请求体注入」的面。
     */
    private static java.util.List<String> instanceFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .toList();
    }
}
