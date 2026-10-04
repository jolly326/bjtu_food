package com.bjtufood.dish.service;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewUpdateReq;

import java.util.List;

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：docs/api/web/views.md。
 *
 * <p>后台只保留三件事：**改文案 / 调顺序 / 显隐**。视图的 `key` / 筛选条件 / 排序口径属
 * seed / 代码资产，**不可通过后台修改**；新增 / 删除 tab 走代码。
 * 约束：**不允许停用"最后一个启用的视图"**（否则端上筛选栏为空、无落地页）。
 */
public interface DishViewAdminService {

    /** 视图列表（按 `order` 升序，带 `matchedCount`） */
    List<DishViewAdminVO> listAll();

    /**
     * 修改视图（仅 `label` + `enabled`；`key` / 条件 / 排序口径不可改）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 视图不存在 /
     *         code=400 停用「最后一个启用的视图」
     */
    void update(Long id, DishViewUpdateReq req);

    /**
     * 排序（拖拽后整体提交全量行）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);
}
