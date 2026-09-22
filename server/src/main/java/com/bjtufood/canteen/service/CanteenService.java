package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.entity.Canteen;

import java.util.List;

/**
 * 食堂服务接口
 * <p>
 * 食堂已去实体化（2026-09-14）：降级为「菜品属性字典」，生命周期仅「新增 / 改名（编辑）」＋后台列表查询，
 * 无删除、无停业/审核能力。
 * <p>
 * 2026-09-22（change「食堂 / 价格筛选全量下线」K4）：**公开侧食堂字典端点已整体删除**——
 * 原 {@code GET /canteens}（含 {@code ?include=stalls}）随筛选功能下线一并移除，故本接口不再提供
 * {@code listCanteens()} / {@code listWithStalls()} 公开查询能力；其公开出参
 * （{@code CanteenInfoVO} / {@code CanteenWithStallsVO} / {@code StallDetailVO}）同批删除。
 */
public interface CanteenService {

    /**
     * 后台食堂列表
     *
     * @return 后台食堂 VO 列表（含完整图片 URL）
     */
    List<CanteenAdminVO> listAllForAdmin();

    /**
     * 编辑食堂
     *
     * @param canteen 食堂信息（含ID）
     * @throws com.bjtufood.common.exception.BusinessException 食堂不存在
     */
    void update(Canteen canteen);
}
