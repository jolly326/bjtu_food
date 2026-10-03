package com.bjtufood.dish.service;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.dish.dto.DishViewAdminVO;
import com.bjtufood.dish.dto.DishViewCondition;
import com.bjtufood.dish.dto.DishViewPreviewVO;
import com.bjtufood.dish.dto.DishViewSaveReq;

import java.util.List;

/**
 * A6 首页筛选视图管理（`/admin/dish-views`）。
 *
 * <p>契约真源：docs/web/A-主数据维护/A6-首页筛选视图管理.md。
 *
 * <p>三条约束：① `key` 在用后不可改（历史端上缓存的 `view=` 会对不上）；
 * ② **默认视图不可删除、不可停用**（与「默认视图恒下发」的下发规则同源，避免自相矛盾）；
 * ③ 条件字段 / 操作符 / 取值一律走白名单（{@code DishViewConditions}），**不可写 SQL**。
 */
public interface DishViewAdminService {

    /** 视图列表（按 `order` 升序，带 `matchedCount`） */
    List<DishViewAdminVO> listAll();

    /**
     * 新建视图（**恒为非默认**；`order` 默认排最后）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 键非法 / 重名、文案非法、条件或排序口径不在白名单
     */
    DishViewAdminVO create(DishViewSaveReq req);

    /**
     * 修改视图（`key` 不可改；文案 / 条件 / 排序 / 启停）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 视图不存在 / code=400 改键、停用默认视图、条件或排序口径非法
     */
    void update(Long id, DishViewSaveReq req);

    /**
     * 删除视图（**默认视图 → `400`**；其余无引用约束 —— 条件只是查询，不持有菜品数据）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在 / code=400 默认视图
     */
    void delete(Long id);

    /**
     * 排序（拖拽后整体提交全量行）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);

    /**
     * 设为默认（**自动取消原默认**，同事务；保证全站恰一个）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 不存在
     */
    void setDefault(Long id);

    /**
     * 预览匹配菜品数（保存前试算，**不入库**）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 条件不在白名单
     */
    DishViewPreviewVO preview(List<DishViewCondition> conditions);
}
