package com.bjtufood.dish.service;

import com.bjtufood.dish.dto.DishCategoryAdminVO;

import java.util.List;

/**
 * A6 分类值字典管理（`/admin/dish-categories`）。
 *
 * <p>数据锚在 `id`（`dish.meal_type` 存的就是它）⇒ 改名免费。
 * `key` 是**代码锚点**（内置视图常量 `DishViewDefs` 按 `key` 引用分类），新建时选填、
 * 缺省由后端自动生成；在用后不可改。
 */
public interface DishCategoryAdminService {

    /** 分类值列表（按 `order` 升序，带 `dishCount`） */
    List<DishCategoryAdminVO> listAll();

    /**
     * 登记新分类值（`POST /admin/dish-categories`）。
     *
     * @param key   分类键（小写字母 / 数字 / `-`，1~20；**选填**，缺省自动生成 `cat-` + 8 位小写十六进制）
     * @param label 分类中文名（1~32 字）
     * @throws com.bjtufood.common.exception.BusinessException code=400 键非法 / 键重名 / 名非法 / 名重名
     */
    DishCategoryAdminVO create(String key, String label);

    /**
     * 重命名（**只改 `label`**，改名免费）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 分类不存在 / code=400 名非法或重名
     */
    void rename(Long id, String label);

    /**
     * 分类 ID 存在性校验（A3 菜品保存调用：`mealTypeId` 必须在字典内）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 分类值不存在
     */
    void requireExists(Long categoryId);
}
