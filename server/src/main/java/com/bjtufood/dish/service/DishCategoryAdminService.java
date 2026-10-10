package com.bjtufood.dish.service;

import com.bjtufood.dish.dto.DishCategoryAdminVO;

import java.util.List;

/**
 * A6 菜品种类字典管理（`/admin/dish-categories`）。
 *
 * <p>字典真源 = {@code dish_category}（本接口是它的唯一读写面）。数据锚在 `id`
 * （`dish.category_id` 存的就是它）⇒ **改名免费**、零菜品迁移。
 *
 * <p>契约真源：docs/api/web/categories.md。
 */
public interface DishCategoryAdminService {

    /** 种类列表（按 `id` 升序；带 `dishCount`） */
    List<DishCategoryAdminVO> listAll();

    /**
     * 登记新种类（`POST /admin/dish-categories`）。
     *
     * @param name 种类中文名（1~32 字，全表唯一）
     * @throws com.bjtufood.common.exception.BusinessException code=400 名非法 / 重名
     */
    DishCategoryAdminVO create(String name);

    /**
     * 重命名（**只改 `name`**，改名免费）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 种类不存在 / code=400 名非法或重名
     */
    void rename(Long id, String name);

    /**
     * 种类存在性校验（A3 菜品保存与 A6 视图保存调用：`categoryId` 必须是 `dish_category` 的行）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 种类不存在
     */
    void requireExists(Long categoryId);
}
