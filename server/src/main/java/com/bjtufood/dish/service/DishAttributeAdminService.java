package com.bjtufood.dish.service;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.dish.dto.DishDimensionAdminVO;
import com.bjtufood.dish.dto.DishValueAdminVO;

import java.util.List;
import java.util.Map;

/**
 * A4 属性维度与取值管理（管理端）+ **属性值的读写口径转换**。
 *
 * <p>契约真源：docs/api/web/dimensions.md。
 *
 * <p><b>模型</b>：取值有独立行与 ID（`dish_attribute_value`），`dish.attributes` 存**取值 ID**
 * （`single` 数字 / `multi` 数字数组），出参翻译成中文 ⇒ 客户端展示契约不变、**改名免费**。
 *
 * <p>三条约束：① `fieldKey` 在用后不可改；② 维度 / 取值**被引用不可删**（`400`）；
 * ③ `valueType` 切换触发该维度下菜品数据的**形状迁移**（单值 `id` ↔ 多值 `[id]`）。
 */
public interface DishAttributeAdminService {

    /** 维度列表（按 `order` 升序，带 `dishCount` / `valueCount`） */
    List<DishDimensionAdminVO> listDimensions();

    /** 新增维度（默认排最后） */
    DishDimensionAdminVO createDimension(String fieldKey, String name, String valueType);

    /** 修改维度（`fieldKey` 不可改；`valueType` 切换自动迁移数据形状） */
    void updateDimension(Long id, String name, String valueType);

    /** 删除维度（被引用 → 400，含其下取值一并删除前的引用校验） */
    void deleteDimension(Long id);

    /** 维度排序（拖拽后整体提交全量行） */
    void sortDimensions(List<SortItem> items);

    /** 某维度下的取值列表（按 `order` 升序，带 `dishCount`） */
    List<DishValueAdminVO> listValues(Long dimensionId);

    /** 新增取值（同维度下 `label` 唯一，默认排最后） */
    DishValueAdminVO createValue(Long dimensionId, String label);

    /** 改名（只改 `label`，改名免费） */
    void updateValue(Long dimensionId, Long valueId, String label);

    /** 删除取值（被菜品引用 → 400） */
    void deleteValue(Long dimensionId, Long valueId);

    /** 取值排序（拖拽后整体提交全量行） */
    void sortValues(Long dimensionId, List<SortItem> items);

    /**
     * **写入口径转换**：把请求里的 `attributes`（值 = 取值 ID 或**中文名**）解析为**取值 ID** 形态。
     * <p>
     * - 传 ID：校验该 ID 属于本次维度（未知 ID → `400`）；
     * - 传中文名：同维度内查字典，命中即用；**未命中自动登记**为新取值再返回其 ID；
     * - 空数组 / null ⇒ **删除该维度键**（清空语义，与纠错采纳合并口径一致）。
     *
     * @param raw 请求原值（键 = 维度 `fieldKey`）
     * @return 可入库的 ID 形态映射（`single` → Long；`multi` → List&lt;Long&gt;）
     * @throws com.bjtufood.common.exception.BusinessException code=400 维度键不在白名单 / 值形态非法 / 未知 ID
     */
    Map<String, Object> resolveForWrite(Map<String, Object> raw);

    /**
     * **出参口径转换**：把库里的 `attributes`（取值 ID）翻译成**中文**（键 = 维度 `fieldKey`）。
     * <p>
     * 找不到对应取值的值（过渡期的历史中文值 / 悬空 ID）**原样保留**，不丢数据、不报错。
     */
    Map<String, Object> translateForRead(String attributesJson);

    /** 该维度下的取值 `id → 中文`（供出参翻译批量复用） */
    Map<Long, String> labelByIdForDimension(Long dimensionId);
}
