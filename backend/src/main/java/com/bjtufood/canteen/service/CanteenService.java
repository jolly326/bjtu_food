package com.bjtufood.canteen.service;

import com.bjtufood.canteen.entity.Canteen;

import java.util.List;

/**
 * 食堂服务接口
 * <p>
 * 食堂和档口的查询与管理，食堂管理员只能查看，系统管理员可增删改。
 */
public interface CanteenService {

    /**
     * 获取所有食堂列表（含下属档口信息）
     * <p>
     * 前端展示用，返回层级结构：
     * [
     *   { id: 1, name: "第一食堂", description: "...", stalls: [{ id: 1, name: "面食窗口"}, ...] },
     *   ...
     * ]
     *
     * @return 食堂列表（含档口）
     */
    List<Canteen> listWithStalls();

    /**
     * 新增食堂
     *
     * @param canteen 食堂信息
     * @throws com.bjtufood.common.exception.BusinessException 名称已存在
     */
    void add(Canteen canteen);

    /**
     * 编辑食堂
     *
     * @param canteen 食堂信息（含ID）
     * @throws com.bjtufood.common.exception.BusinessException 食堂不存在
     */
    void update(Canteen canteen);

    /**
     * 删除食堂
     * <p>
     * 约束：如果食堂下还有档口，禁止删除
     *
     * @param id 食堂ID
     * @throws com.bjtufood.common.exception.BusinessException 食堂下有档口
     */
    void delete(Long id);
}
