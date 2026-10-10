package com.bjtufood.dish.service;

import com.bjtufood.common.dto.SortItem;
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

    /** 种类列表（按 `order` 升序，同序按 `id` 升序；带 `dishCount`） */
    List<DishCategoryAdminVO> listAll();

    /**
     * 登记新种类（`POST /admin/dish-categories`）。
     *
     * @param label 种类中文名（1~32 字，全表唯一）
     * @throws com.bjtufood.common.exception.BusinessException code=400 名非法 / 重名
     */
    DishCategoryAdminVO create(String label);

    /**
     * 重命名（**只改 `label`**，改名免费）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 种类不存在 / code=400 名非法或重名
     */
    void rename(Long id, String label);

    /**
     * 拖拽排序（`PUT /admin/dish-categories/sort`）：整体替换全表 `order`。
     *
     * <p>提交协议与其余 `PUT .../sort` 一致（见 {@code docs/api/README.md}〈拖拽排序提交〉）：
     * `items` 必须是**全量行**，缺行 / 重复即 `400`。
     *
     * @param items 全量排序行（`{ id, order }`）
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);

    /**
     * 种类存在性校验（A3 菜品保存调用：`categoryId` 必须是 `dish_category` 的行）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 种类不存在
     */
    void requireExists(Long categoryId);
}
