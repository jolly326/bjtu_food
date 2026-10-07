package com.bjtufood.dish.service;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.dish.dto.DishCategoryAdminVO;

import java.util.List;

/**
 * A6 菜品种类字典管理（`/admin/dish-categories`）。
 *
 * <p>本接口是**系统维度（菜品种类）取值的别名面**：同一批行也可经 A4 的取值端点读写
 * （`/admin/dish-dimensions/{systemDimensionId}/values`），两条路径操作同一份数据。
 * 数据锚在取值 `id`（`dish.meal_type_id` 存的就是它）⇒ **改名免费**。
 */
public interface DishCategoryAdminService {

    /** 种类取值列表（按组内 `order` 升序，带 `dishCount`） */
    List<DishCategoryAdminVO> listAll();

    /**
     * 登记新种类取值（`POST /admin/dish-categories`）。
     *
     * @param label 种类中文名（1~32 字，同维度下唯一）
     * @throws com.bjtufood.common.exception.BusinessException code=400 名非法 / 重名
     */
    DishCategoryAdminVO create(String label);

    /**
     * 重命名（**只改 `label`**，改名免费）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 取值不存在 / code=400 名非法或重名
     */
    void rename(Long id, String label);

    /**
     * 拖拽排序（`PUT /admin/dish-categories/sort`）：整体替换组内 `order`。
     *
     * <p>提交协议与其余 `PUT .../sort` 一致（见 {@code docs/api/README.md}〈拖拽排序提交〉）：
     * `items` 必须是**全量行**，缺行 / 重复即 `400`。
     *
     * @param items 全量排序行（`{ id, order }`）
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);

    /**
     * 种类取值存在性校验（A3 菜品保存调用：`mealTypeId` 必须属于系统维度「菜品种类」）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 取值不属于系统维度
     */
    void requireExists(Long valueId);
}
