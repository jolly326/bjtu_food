package com.bjtufood.dish.service;

import com.bjtufood.dish.dto.DishCategoryAdminVO;

import java.util.List;

/**
 * A6 分类值字典管理（`/admin/dish-categories`）+ 菜品录入的**值域校验与自动登记**。
 *
 * <p>分类值由**自由输入产生**（A3 菜品录入），后台只保留**重命名**。
 * 数据锚在 `key`（`dish.meal_type` 存的就是它）⇒ 改名免费。
 */
public interface DishCategoryAdminService {

    /** 分类值列表（按 `order` 升序，带 `dishCount`） */
    List<DishCategoryAdminVO> listAll();

    /**
     * 登记新分类值（`POST /admin/dish-categories`）。
     *
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
     * 菜品录入时的值域校验与**自动登记**（A3 调用）：
     * `key` 已存在 → 原样返回；不存在 → 以 `key` 为键、`key` 为初名登记一行后返回。
     *
     * @param key 分类键（小写字母 / 数字 / `-`，1~20）
     * @return 规范化后的分类键
     * @throws com.bjtufood.common.exception.BusinessException code=400 键非法（空 / 超长 / 非法字符）
     */
    String resolveOrRegister(String key);
}
