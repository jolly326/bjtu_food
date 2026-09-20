package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.CanteenWithStallsVO;
import com.bjtufood.canteen.entity.Canteen;

import java.util.List;

/**
 * 食堂服务接口
 * <p>
 * 食堂已去实体化（2026-09-14）：降级为「菜品筛选属性字典」，生命周期仅「新增 / 改名（编辑）」＋列表查询，
 * 无删除、无停业/审核能力。
 */
public interface CanteenService {

    /**
     * 获取食堂列表（首页用于展示）
     * <p>
     * 坐标与距离已全链下线（2026-09-20 拍板）：VO 不含坐标，服务端不计算也不下发距离。
     * 全量返回（无停业语义，不再按 status 过滤），按 sort_order 排序。
     *
     * @return 食堂展示列表
     */
    List<CanteenInfoVO> listCanteens();

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
    List<CanteenWithStallsVO> listWithStalls();

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
