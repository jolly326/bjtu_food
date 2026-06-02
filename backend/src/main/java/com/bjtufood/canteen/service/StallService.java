package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Stall;

import java.util.List;

/**
 * 档口服务接口
 * <p>
 * 档口的查询和管理，一个档口从属于一个食堂。
 */
public interface StallService {

    /**
     * 按食堂ID查询档口列表
     *
     * @param canteenId 食堂ID
     * @return 该食堂下的档口列表
     */
    List<StallDetailVO> listByCanteenId(Long canteenId);

    /**
     * 新增档口
     *
     * @param stall 档口信息
     * @throws com.bjtufood.common.exception.BusinessException 食堂不存在
     */
    void add(Stall stall);

    /**
     * 编辑档口
     *
     * @param stall 档口信息（含ID）
     */
    void update(Stall stall);

    /**
     * 删除档口
     * <p>
     * 约束：如果档口下还有菜品，禁止删除
     *
     * @param id 档口ID
     * @throws com.bjtufood.common.exception.BusinessException 档口下有菜品
     */
    void delete(Long id);

    /**
     * 根据ID查询档口
     *
     * @param id 档口ID
     * @return 档口实体
     * @throws com.bjtufood.common.exception.BusinessException 档口不存在
     */
    Stall getById(Long id);
}
