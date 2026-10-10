package com.bjtufood.dish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.dish.dto.DishDimensionAdminVO;
import com.bjtufood.dish.dto.DishValueAdminVO;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.entity.DishAttributeValue;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishAttributeValueMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishAttributeCatalog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A4 属性维度 / 取值管理端的契约单测（口径见 docs/api/web/dimensions.md、
 * docs/schema/dish_attribute_value.md）：
 * <ol>
 *   <li><b>维度</b>：名称必填与 32 字上限、取值类型白名单、被菜品引用不可删
 *       （含「其下取值随维度一并清理」的落库顺序）；</li>
 *   <li><b>取值</b>：归属维度必须存在、name 必填与 32 字上限、同维度重名、被菜品引用不可删；</li>
 *   <li><b>形状迁移</b>：`valueType` 单 ⇄ 多切换时按维度 ID 迁移 `dish.attributes`（标量 ↔ 数组，
 *       幂等跳过已是目标形状的行）；</li>
 *   <li><b>读写口径转换</b>：`resolveForWrite`（键 = 维度 ID / 取值 ID 校验 / 中文名查字典 /
 *       未命中自动登记 / null 与空数组 = 清空）与 `translateForRead`（取值 ID → 中文，悬空 ID 原样保留）。</li>
 * </ol>
 * 断言口径为「错误码 + 文案 + 落库字段 / 不写库」三锁：只断言抛异常无法区分 {@code 400}（表单非法）
 * 与 {@code 4001}（目标不存在）的语义差异，也无法发现「报错前已经写库」。
 */
class DishAttributeAdminServiceImplTest {

    private static final String MSG_VALUE_TYPE_ILLEGAL = "取值类型非法（仅 single / multi）";

    /** MyBatis-Plus 的 lambda 缓存需显式初始化，否则构造 LambdaQueryWrapper 会抛异常 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                DishAttributeAdminServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, DishAttributeDimension.class);
        TableInfoHelper.initTableInfo(assistant, DishAttributeValue.class);
        TableInfoHelper.initTableInfo(assistant, Dish.class);
    }

    private final DishAttributeDimensionMapper dimensionMapper = mock(DishAttributeDimensionMapper.class);
    private final DishAttributeValueMapper valueMapper = mock(DishAttributeValueMapper.class);
    private final DishMapper dishMapper = mock(DishMapper.class);
    private final DishAttributeCatalog attributeCatalog = mock(DishAttributeCatalog.class);

    private DishAttributeAdminServiceImpl service() {
        return new DishAttributeAdminServiceImpl(dimensionMapper, valueMapper, dishMapper, attributeCatalog);
    }

    // ==================== 夹具 ====================

    private static DishAttributeDimension dimension(Long id, String name, String valueType) {
        DishAttributeDimension d = new DishAttributeDimension();
        d.setId(id);
        d.setName(name);
        d.setValueType(valueType);
        return d;
    }

    private static DishAttributeValue value(Long id, Long dimensionId, String name) {
        DishAttributeValue v = new DishAttributeValue();
        v.setId(id);
        v.setDimensionId(dimensionId);
        v.setName(name);
        return v;
    }

    private static Dish dish(long id, String attributesJson) {
        Dish d = new Dish();
        d.setId(id);
        d.setAttributes(attributesJson);
        return d;
    }

    /** 维度 1「饮食属性」（single）；`dish.attributes` 的键 = "1" */
    private static DishAttributeDimension singleDim() {
        return dimension(1L, "饮食属性", "single");
    }

    /** 维度 2「口味」（multi）；`dish.attributes` 的键 = "2" */
    private static DishAttributeDimension multiDim() {
        return dimension(2L, "口味", "multi");
    }

    /** 断言「业务码 + 文案」双锁（避免每个用例重复强制转换样板）。 */
    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                       int code, String message) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code));
    }

    /** 取本次 {@code updateById} 提交的局部实体（只带本次可编辑列）。 */
    private static <T> T capturedPatch(Class<T> type, BaseMapper<T> mapper) {
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(type);
        verify(mapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ==================== 维度：listDimensions ====================

    @Test
    @DisplayName("listDimensions：dishCount 按维度 ID 聚合、valueCount 按维度聚合，未被引用一律 0")
    void listDimensions_aggregatesCounts() {
        when(dimensionMapper.selectList(any())).thenReturn(List.of(singleDim(), multiDim()));
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(5L, 1L, "半荤"), value(6L, 1L, "全素"), value(7L, 2L, "微辣")));
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, "{\"1\":5}"),
                // 空数组不计入 dishCount（清空语义）
                dish(2L, "{\"1\":[5,6],\"2\":[]}"),
                dish(3L, "{\"legacy\":\"半荤\"}")));

        List<DishDimensionAdminVO> list = service().listDimensions();

        assertThat(list).hasSize(2);
        DishDimensionAdminVO first = list.get(0);
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(first.getName()).isEqualTo("饮食属性");
        assertThat(first.getValueType()).isEqualTo("single");
        // 维度 1 被 2 行菜品使用；其下有 2 个取值
        assertThat(first.getDishCount()).isEqualTo(2L);
        assertThat(first.getValueCount()).isEqualTo(2L);

        DishDimensionAdminVO second = list.get(1);
        // 维度 2 仅出现空数组 ⇒ 视同未使用
        assertThat(second.getDishCount()).isZero();
        assertThat(second.getValueCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listDimensions：无任何引用数据 → 两个计数均为 0（不返回 null）")
    void listDimensions_zeroCountsWhenUnused() {
        when(dimensionMapper.selectList(any())).thenReturn(List.of(singleDim()));

        DishDimensionAdminVO vo = service().listDimensions().get(0);

        assertThat(vo.getDishCount()).isZero();
        assertThat(vo.getValueCount()).isZero();
    }

    // ==================== 维度：createDimension ====================

    @Test
    @DisplayName("createDimension：正常落库 —— 名称去空白，出参计数为 0，并失效目录缓存")
    void createDimension_savesTrimmedName() {
        AtomicReference<DishAttributeDimension> inserted = new AtomicReference<>();
        when(dimensionMapper.insert(any())).thenAnswer(invocation -> {
            DishAttributeDimension entity = invocation.getArgument(0);
            entity.setId(9L);
            inserted.set(entity);
            return 1;
        });
        when(dimensionMapper.selectById(9L)).thenAnswer(invocation -> inserted.get());

        DishDimensionAdminVO vo = service().createDimension("  饮食属性  ", "single");

        DishAttributeDimension saved = inserted.get();
        assertThat(saved.getName()).isEqualTo("饮食属性");
        assertThat(saved.getValueType()).isEqualTo("single");
        assertThat(vo.getId()).isEqualTo(9L);
        assertThat(vo.getDishCount()).isZero();
        assertThat(vo.getValueCount()).isZero();
        verify(attributeCatalog).invalidateDimensions();
        verify(attributeCatalog).invalidateCandidates();
    }

    @Test
    @DisplayName("createDimension：维度名空白 / 超 32 字 → 400，且不落库")
    void createDimension_illegalName_rejected400() {
        assertRejected(() -> service().createDimension("   ", "single"), 400, "维度名不能为空");
        assertRejected(() -> service().createDimension(null, "single"), 400, "维度名不能为空");
        assertRejected(() -> service().createDimension("名".repeat(33), "single"),
                400, "维度名不能超过 32 字");

        verify(dimensionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createDimension：取值类型非 single / multi（含大小写变形、空）→ 400，且不落库")
    void createDimension_illegalValueType_rejected400() {
        for (String illegal : List.of("SINGLE", "both", "", "  ")) {
            assertRejected(() -> service().createDimension("饮食属性", illegal),
                    400, MSG_VALUE_TYPE_ILLEGAL);
        }
        assertRejected(() -> service().createDimension("饮食属性", null), 400, MSG_VALUE_TYPE_ILLEGAL);

        verify(dimensionMapper, never()).insert(any());
    }

    // ==================== 维度：updateDimension ====================

    @Test
    @DisplayName("updateDimension：维度不存在 → 4001「维度不存在」，且不写库")
    void updateDimension_missing_rejected4001() {
        assertRejected(() -> service().updateDimension(1L, "饮食属性", "single"), 4001, "维度不存在");

        verify(dimensionMapper, never()).updateById(any());
        verifyNoInteractions(dishMapper);
    }

    @Test
    @DisplayName("updateDimension：名称空白 / 超 32 字 → 400，且不写库")
    void updateDimension_illegalName_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());

        assertRejected(() -> service().updateDimension(1L, " ", "single"), 400, "维度名不能为空");
        assertRejected(() -> service().updateDimension(1L, "名".repeat(33), "single"), 400, "维度名不能超过 32 字");

        verify(dimensionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateDimension：取值类型非法 → 400，且不写库、不迁移数据")
    void updateDimension_illegalValueType_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());

        assertRejected(() -> service().updateDimension(1L, "饮食属性", "both"), 400, MSG_VALUE_TYPE_ILLEGAL);

        verify(dimensionMapper, never()).updateById(any());
        verifyNoInteractions(dishMapper);
    }

    @Test
    @DisplayName("updateDimension：局部实体只带 name / valueType（ID 锚点不可改）；类型不变则不迁移数据")
    void updateDimension_onlyEditableColumnsAndNoMigration() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(dimensionMapper.updateById(any())).thenReturn(1);

        service().updateDimension(1L, "  口味  ", "single");

        DishAttributeDimension patch = capturedPatch(DishAttributeDimension.class, dimensionMapper);
        assertThat(patch.getId()).isEqualTo(1L);
        assertThat(patch.getName()).isEqualTo("口味");
        assertThat(patch.getValueType()).isEqualTo("single");
        verifyNoInteractions(dishMapper);
    }

    @Test
    @DisplayName("updateDimension：single → multi 迁移 —— 标量包成单元素数组、null 成空数组、已是数组与无该键的行跳过")
    void updateDimension_singleToMulti_migratesShape() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(dimensionMapper.updateById(any())).thenReturn(1);
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, "{\"1\":5}"),
                dish(2L, "{\"1\":[5,6]}"),
                dish(3L, "{\"2\":3}"),
                dish(4L, "{\"1\":null}"),
                dish(5L, null)));

        service().updateDimension(1L, "饮食属性", "multi");

        Map<Long, Map<String, Object>> migrated = capturedDishUpdates(2);
        assertThat(migrated.get(1L)).containsEntry("1", List.of(5));
        // null 视同「空的多值」
        assertThat(migrated.get(4L)).containsEntry("1", List.of());
        // 已是数组 / 无该维度键 / attributes 为 null 的行都不产生写库
        assertThat(migrated).doesNotContainKeys(2L, 3L, 5L);
    }

    @Test
    @DisplayName("updateDimension：multi → single 迁移 —— 数组取首项、空数组成 null、已是标量的行跳过")
    void updateDimension_multiToSingle_migratesShape() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(dimensionMapper.updateById(any())).thenReturn(1);
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, "{\"2\":[7,8]}"),
                dish(2L, "{\"2\":[]}"),
                dish(3L, "{\"2\":7}"),
                dish(4L, "{\"1\":5}")));

        service().updateDimension(2L, "口味", "single");

        Map<Long, Map<String, Object>> migrated = capturedDishUpdates(2);
        assertThat(migrated.get(1L)).containsEntry("2", 7);
        // 空数组退化为 null：键保留（清空语义由出参翻译兜底），不是删键
        assertThat(migrated.get(2L)).containsOnly(entry("2", null));
        assertThat(migrated).doesNotContainKeys(3L, 4L);
    }

    /** 取本次迁移写入的所有菜品行，并按 id 汇总「维度键 → 值」（已解析回 Map 便于断言）。 */
    private Map<Long, Map<String, Object>> capturedDishUpdates(int expectedWrites) {
        ArgumentCaptor<Dish> captor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper, times(expectedWrites)).updateById(captor.capture());
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Dish patch : captor.getAllValues()) {
            byId.put(patch.getId(), JsonMapUtil.parseObject(patch.getAttributes()));
        }
        return byId;
    }

    // ==================== 维度：deleteDimension ====================

    @Test
    @DisplayName("deleteDimension：维度不存在 → 4001「维度不存在」，且不删任何行")
    void deleteDimension_missing_rejected4001() {
        assertRejected(() -> service().deleteDimension(1L), 4001, "维度不存在");

        verify(dimensionMapper, never()).deleteById(anyLong());
        verify(valueMapper, never()).delete(any());
    }

    @Test
    @DisplayName("deleteDimension：仍被菜品使用 → 400（文案含被引用数），且不删维度、不删其下取值")
    void deleteDimension_referenced_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectList(any())).thenReturn(List.of(value(5L, 1L, "半荤")));
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, "{\"1\":5}"),
                // 空数组不计入引用
                dish(2L, "{\"1\":[]}"),
                dish(3L, "{\"2\":7}")));

        assertRejected(() -> service().deleteDimension(1L), 400,
                "仍有 1 个菜品使用该维度，不能删除（请先改菜品）");

        verify(dimensionMapper, never()).deleteById(anyLong());
        verify(valueMapper, never()).delete(any());
    }

    @Test
    @DisplayName("deleteDimension：未被引用 → 先清其下取值再删维度（取值无独立引用主体）")
    void deleteDimension_unused_clearsValuesThenDimension() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(dishMapper.selectList(any())).thenReturn(List.of(dish(1L, "{\"2\":7}")));

        service().deleteDimension(1L);

        verify(valueMapper).delete(any());
        verify(dimensionMapper).deleteById(1L);
    }

    // ==================== 取值：listValues / createValue ====================

    @Test
    @DisplayName("listValues：维度不存在（含 null / 0 起无效值）→ 4001「维度不存在」")
    void listValues_missingDimension_rejected4001() {
        assertRejected(() -> service().listValues(1L), 4001, "维度不存在");
        assertRejected(() -> service().listValues(null), 4001, "维度不存在");
    }

    @Test
    @DisplayName("listValues：出参带 dishCount（按取值 ID 聚合引用菜品数）")
    void listValues_carriesDishCount() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(7L, 2L, "微辣"), value(8L, 2L, "特辣")));
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, "{\"2\":[7,8]}"),
                dish(2L, "{\"2\":7}")));

        List<DishValueAdminVO> values = service().listValues(2L);

        assertThat(values).hasSize(2);
        assertThat(values.get(0).getId()).isEqualTo(7L);
        assertThat(values.get(0).getDimensionId()).isEqualTo(2L);
        assertThat(values.get(0).getName()).isEqualTo("微辣");
        assertThat(values.get(0).getDishCount()).isEqualTo(2L);
        assertThat(values.get(1).getDishCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createValue：维度不存在先于 name 校验 → 4001（不因 name 为空而报 400）")
    void createValue_missingDimension_rejected4001() {
        assertRejected(() -> service().createValue(1L, "  "), 4001, "维度不存在");

        verify(valueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createValue：name 空白 / 超 32 字 → 400，且不落库")
    void createValue_illegalName_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());

        assertRejected(() -> service().createValue(1L, "   "), 400, "取值名不能为空");
        assertRejected(() -> service().createValue(1L, null), 400, "取值名不能为空");
        assertRejected(() -> service().createValue(1L, "值".repeat(33)), 400, "取值名不能超过 32 字");

        verify(valueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createValue：同维度下重名 → 400「该取值已存在」，且不落库")
    void createValue_duplicateName_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectCount(any())).thenReturn(1L);

        assertRejected(() -> service().createValue(1L, "半荤"), 400, "该取值已存在");

        verify(valueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createValue：正常落库 —— name 去空白，出参含 dimensionId 且 dishCount = 0")
    void createValue_savesTrimmedName() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectCount(any())).thenReturn(0L);
        when(valueMapper.selectList(any())).thenReturn(List.of(value(5L, 1L, "半荤")));
        AtomicReference<DishAttributeValue> inserted = new AtomicReference<>();
        when(valueMapper.insert(any())).thenAnswer(invocation -> {
            DishAttributeValue entity = invocation.getArgument(0);
            entity.setId(31L);
            inserted.set(entity);
            return 1;
        });
        when(valueMapper.selectById(31L)).thenAnswer(invocation -> inserted.get());

        DishValueAdminVO vo = service().createValue(1L, "  全素  ");

        assertThat(inserted.get().getDimensionId()).isEqualTo(1L);
        assertThat(inserted.get().getName()).isEqualTo("全素");
        assertThat(vo.getId()).isEqualTo(31L);
        assertThat(vo.getDimensionId()).isEqualTo(1L);
        assertThat(vo.getName()).isEqualTo("全素");
        assertThat(vo.getDishCount()).isZero();
    }

    // ==================== 取值：updateValue / deleteValue ====================

    @Test
    @DisplayName("updateValue：维度不存在 / 取值不存在 / 取值不属于该维度 → 一律 4001「…不存在」，且不写库")
    void updateValue_unresolvedTarget_rejected4001() {
        // 维度不存在
        assertRejected(() -> service().updateValue(1L, 5L, "半荤"), 4001, "维度不存在");

        // 维度存在但取值不存在（valueId 为 null / 查无此值）
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        assertRejected(() -> service().updateValue(1L, 5L, "半荤"), 4001, "取值不存在");
        assertRejected(() -> service().updateValue(1L, null, "半荤"), 4001, "取值不存在");

        // 取值属于另一个维度（防跨维度改值）
        when(valueMapper.selectById(7L)).thenReturn(value(7L, 2L, "微辣"));
        assertRejected(() -> service().updateValue(1L, 7L, "微辣"), 4001, "取值不存在");

        verify(valueMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateValue：name 空白 → 400「取值名不能为空」；重名 → 400「该取值已存在」；均不写库")
    void updateValue_illegalOrDuplicateName_rejected400() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectById(7L)).thenReturn(value(7L, 2L, "微辣"));

        assertRejected(() -> service().updateValue(2L, 7L, "  "), 400, "取值名不能为空");

        // 同维度下已有同名取值（查重时排除自身）
        when(valueMapper.selectCount(any())).thenReturn(1L);
        assertRejected(() -> service().updateValue(2L, 7L, "特辣"), 400, "该取值已存在");

        verify(valueMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateValue：改名只写 name（菜品数据零迁移）")
    void updateValue_writesNameOnly() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectById(7L)).thenReturn(value(7L, 2L, "微辣"));
        when(valueMapper.selectCount(any())).thenReturn(0L);
        when(valueMapper.updateById(any())).thenReturn(1);

        service().updateValue(2L, 7L, "  中辣  ");

        DishAttributeValue patch = capturedPatch(DishAttributeValue.class, valueMapper);
        assertThat(patch.getId()).isEqualTo(7L);
        assertThat(patch.getName()).isEqualTo("中辣");
        assertThat(patch.getDimensionId()).isNull();
        verifyNoInteractions(dishMapper);
    }

    @Test
    @DisplayName("deleteValue：取值不存在 / 不属于该维度 → 4001「取值不存在」，且不删库")
    void deleteValue_unresolvedTarget_rejected4001() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());

        assertRejected(() -> service().deleteValue(1L, 5L), 4001, "取值不存在");

        when(valueMapper.selectById(7L)).thenReturn(value(7L, 2L, "微辣"));
        assertRejected(() -> service().deleteValue(1L, 7L), 4001, "取值不存在");

        verify(valueMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteValue：仍被菜品引用 → 400（文案含被引用菜品数），且不删库")
    void deleteValue_referenced_rejected400() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectById(7L)).thenReturn(value(7L, 2L, "微辣"));
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(5L, 2L, "不辣"), value(7L, 2L, "微辣")));
        when(dishMapper.selectList(any())).thenReturn(List.of(
                dish(1L, "{\"2\":[5,7]}"),
                dish(2L, "{\"2\":7}")));

        assertRejected(() -> service().deleteValue(2L, 7L), 400, "仍有 2 个菜品引用该取值，不能删除");

        verify(valueMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteValue：未被引用 → 删除该取值行（同维度其它取值不受影响）")
    void deleteValue_unused_deletesRow() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectById(7L)).thenReturn(value(7L, 2L, "微辣"));
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(5L, 2L, "不辣"), value(7L, 2L, "微辣")));
        when(dishMapper.selectList(any())).thenReturn(List.of(dish(1L, "{\"2\":[5]}")));
        when(valueMapper.deleteById(anyLong())).thenReturn(1);

        service().deleteValue(2L, 7L);

        verify(valueMapper).deleteById(7L);
    }

    // ==================== 写入口径：resolveForWrite ====================

    @Test
    @DisplayName("resolveForWrite：null / 空 Map → 空结果（无属性写入）")
    void resolveForWrite_emptyInput_returnsEmpty() {
        assertThat(service().resolveForWrite(null)).isEmpty();
        assertThat(service().resolveForWrite(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("resolveForWrite：键不是维度表内的维度 ID → 400「未知的属性维度：…」")
    void resolveForWrite_unknownDimensionKey_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());

        // 数字键但维度不存在 / 非数字键
        assertRejected(() -> service().resolveForWrite(Map.of("99", 5)), 400, "未知的属性维度：99");
        assertRejected(() -> service().resolveForWrite(Map.of("nope", 5)), 400, "未知的属性维度：nope");
    }

    @Test
    @DisplayName("resolveForWrite：值为 null / 空数组 → 该维度键不落（清空语义）")
    void resolveForWrite_nullOrEmptyArray_clearsKey() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("1", null);
        raw.put("2", List.of());

        assertThat(service().resolveForWrite(raw)).isEmpty();
    }

    @Test
    @DisplayName("resolveForWrite：单值维度收到数组 → 取首项（宽容前端形态差异）")
    void resolveForWrite_singleDimensionWithArray_takesFirstItem() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectOne(any())).thenReturn(value(5L, 1L, "半荤"));

        Map<String, Object> result = service().resolveForWrite(Map.of("1", List.of(5, 6)));

        assertThat(result).containsOnly(entry("1", 5L));
    }

    @Test
    @DisplayName("resolveForWrite：多值维度恒为 ID 数组（标量入参包成单元素数组）")
    void resolveForWrite_multiDimension_alwaysReturnsArray() {
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectOne(any())).thenReturn(value(5L, 2L, "不辣"));

        Map<String, Object> scalar = service().resolveForWrite(Map.of("2", 5));
        assertThat(scalar).containsOnly(entry("2", List.of(5L)));

        // 重新打桩：字典查询按入参次序依次回显 ID 5 / 7（数组形态逐一解析）
        when(valueMapper.selectOne(any())).thenReturn(value(5L, 2L, "不辣"), value(7L, 2L, "微辣"));
        Map<String, Object> array = service().resolveForWrite(Map.of("2", List.of(5, 7)));
        assertThat(array).containsOnly(entry("2", List.of(5L, 7L)));
    }

    @Test
    @DisplayName("resolveForWrite：未知取值 ID / 跨维度 ID / 空字符串取值 → 400，且不自动登记")
    void resolveForWrite_illegalValue_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        // 按 ID 查不到（含「该 ID 属于别的维度」）
        when(valueMapper.selectOne(any())).thenReturn(null);

        assertRejected(() -> service().resolveForWrite(Map.of("1", 5)), 400, "属性取值 ID 不存在：5");
        assertRejected(() -> service().resolveForWrite(Map.of("1", "   ")), 400, "属性取值不能为空");
        assertRejected(() -> service().resolveForWrite(Map.of("1", List.of(""))), 400, "属性取值不能为空");

        verify(valueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("resolveForWrite：中文名命中字典 → 返回既有取值 ID（不新建行）")
    void resolveForWrite_nameHit_returnsExistingId() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectOne(any())).thenReturn(value(9L, 1L, "半荤"));

        Map<String, Object> result = service().resolveForWrite(Map.of("1", " 半荤 "));

        assertThat(result).containsOnly(entry("1", 9L));
        verify(valueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("resolveForWrite：中文名未命中 → 自动登记为新取值")
    void resolveForWrite_nameMiss_registersNewValue() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectOne(any())).thenReturn(null);
        when(valueMapper.selectList(any())).thenReturn(List.of(value(5L, 1L, "半荤")));
        AtomicReference<DishAttributeValue> inserted = new AtomicReference<>();
        when(valueMapper.insert(any())).thenAnswer(invocation -> {
            DishAttributeValue entity = invocation.getArgument(0);
            entity.setId(21L);
            inserted.set(entity);
            return 1;
        });

        Map<String, Object> result = service().resolveForWrite(Map.of("1", " 全素 "));

        assertThat(result).containsOnly(entry("1", 21L));
        assertThat(inserted.get().getDimensionId()).isEqualTo(1L);
        assertThat(inserted.get().getName()).isEqualTo("全素");
    }

    @Test
    @DisplayName("resolveForWrite：未命中的新值超过 32 字 → 400「属性取值不能超过 32 字」，且不登记")
    void resolveForWrite_newNameTooLong_rejected400() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(valueMapper.selectOne(any())).thenReturn(null);

        assertRejected(() -> service().resolveForWrite(Map.of("1", "值".repeat(33))),
                400, "属性取值不能超过 32 字");

        verify(valueMapper, never()).insert(any());
    }

    @Test
    @DisplayName("resolveForWrite：多个维度按键序解析（结果保序，键 = 维度 ID 字符串，便于落库 JSON 稳定）")
    void resolveForWrite_keepsKeyOrder() {
        when(dimensionMapper.selectById(1L)).thenReturn(singleDim());
        when(dimensionMapper.selectById(2L)).thenReturn(multiDim());
        when(valueMapper.selectOne(any())).thenReturn(value(5L, 1L, "半荤"), value(7L, 2L, "微辣"));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("1", 5);
        raw.put("2", List.of(7));

        Map<String, Object> result = service().resolveForWrite(raw);

        assertThat(new ArrayList<>(result.keySet())).containsExactly("1", "2");
    }

    // ==================== 出参口径：translateForRead / nameByIdForDimension ====================

    @Test
    @DisplayName("translateForRead：null / 空白 / 非法 JSON → 空结果（脏列不炸读接口）")
    void translateForRead_emptyOrDirtyJson_returnsEmpty() {
        assertThat(service().translateForRead(null)).isEmpty();
        assertThat(service().translateForRead("  ")).isEmpty();
        assertThat(service().translateForRead("{不是 JSON")).isEmpty();
    }

    @Test
    @DisplayName("translateForRead：取值 ID → 中文；悬空 ID 原样保留（不丢数据）；键 = 维度 ID 字符串")
    void translateForRead_translatesIds() {
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(5L, 1L, "半荤"), value(7L, 2L, "微辣")));

        Map<String, Object> out = service().translateForRead(
                "{\"1\":5,\"2\":[7,8],\"legacy\":\"全素\",\"dangling\":999}");

        assertThat(out).containsOnly(
                entry("1", "半荤"),
                // 8 / 999 无对应字典行：保留原 ID 文本
                entry("2", List.of("微辣", "8")),
                entry("legacy", "全素"),
                entry("dangling", "999"));
    }

    @Test
    @DisplayName("translateForRead：null 值与空数组原样透传；带空白数字串按 ID 翻译")
    void translateForRead_handlesNullAndEmptyArray() {
        when(valueMapper.selectList(any())).thenReturn(List.of(value(5L, 1L, "半荤")));

        Map<String, Object> out = service().translateForRead("{\"1\":null,\"2\":[],\"x\":\" 5 \"}");

        assertThat(out).containsOnly(
                entry("1", null),
                entry("2", List.of()),
                entry("x", "半荤"));
    }

    @Test
    @DisplayName("nameByIdForDimension：返回该维度下的 id → 中文映射")
    void nameByIdForDimension_returnsNames() {
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(7L, 2L, "微辣"), value(8L, 2L, "特辣")));

        assertThat(service().nameByIdForDimension(2L))
                .containsOnly(entry(7L, "微辣"), entry(8L, "特辣"));
    }

}
