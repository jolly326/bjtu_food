package com.bjtufood.dish.service;

import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCreateReq;
import com.bjtufood.dish.dto.DishViewUpdateReq;

import java.util.List;

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：docs/api/web/views.md。
 *
 * <p><b>视图是纯数据</b>：文案 / 启停 / 归属种类全部落库，后台可增删改、**免发版**；
 * 取数口径固定为「种类过滤（可空）+ 随机序 + 在售」。
 * 约束：**不允许停用 / 删除「最后一个启用的视图」**（否则端上筛选栏为空、无落地页）。
 */
public interface DishViewAdminService {

    /** 视图列表（按 `id` 升序，带 `matchedCount`） */
    List<DishViewAdminVO> listAll();

    /**
     * 新建视图（默认启用）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 文案非法 / 种类不存在
     */
    DishViewAdminVO create(DishViewCreateReq req);

    /**
     * 修改视图（`name` / `enabled` / `categoryId` **整体替换**）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 视图不存在 /
     *         code=400 停用「最后一个启用的视图」或字段非法
     */
    void update(Long id, DishViewUpdateReq req);

    /**
     * 删除视图（tab 从下发集合移除）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 视图不存在 /
     *         code=400 删除「最后一个启用的视图」
     */
    void delete(Long id);
}
