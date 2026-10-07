package com.bjtufood.dish.service;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCreateReq;
import com.bjtufood.dish.dto.DishViewUpdateReq;

import java.util.List;

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：docs/api/web/views.md。
 *
 * <p><b>视图是纯数据</b>：文案 / 启停 / 筛选条件 / 排序口径全部落库，后台可增删改、**免发版**；
 * 筛选条件的字段 / 操作符 / 排序口径受白名单约束（唯一真源 `DishViewConditions`）。
 * 约束：**不允许停用 / 删除「最后一个启用的视图」**（否则端上筛选栏为空、无落地页）。
 */
public interface DishViewAdminService {

    /** 视图列表（按 `order` 升序，带 `conditions` / `sortKind` / `matchedCount`） */
    List<DishViewAdminVO> listAll();

    /**
     * 新建视图（默认排最后、默认启用）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 文案 / 条件 / 排序口径非法
     */
    DishViewAdminVO create(DishViewCreateReq req);

    /**
     * 修改视图（`label` / `enabled` / `conditions` / `sortKind` **整体替换**）。
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

    /**
     * 排序（拖拽后整体提交全量行）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);
}
