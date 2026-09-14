package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.entity.Stall;

import java.util.List;

/**
 * 档口服务接口
 * <p>
 * 档口已去实体化（2026-09-14）：降级为「菜品筛选属性字典」，生命周期仅「新增 / 改名（编辑）」＋列表查询，
 * 无删除、无停业/审核能力；一个档口仍从属于一个食堂（canteen_id）。
 */
public interface StallService {

    /**
     * 后台档口列表
     *
     * @return 后台档口 VO 列表（含完整图片 URL）
     */
    List<StallAdminVO> listAllForAdmin();

    /**
     * 编辑档口
     *
     * @param stall 档口信息（含ID）
     */
    void update(Stall stall);
}
