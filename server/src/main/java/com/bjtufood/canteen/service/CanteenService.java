package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.entity.Canteen;

import java.util.List;

/**
 * 食堂服务接口
 * <p>
 * 食堂生命周期 = <b>新增 / 改名 / 删除</b> ＋ 后台列表查询（管理端维护的主数据实体，见
 * docs/func/web/A-主数据维护/A1-食堂管理.md）；**无停业 / 审核能力**。
 * <p>
 * （change「食堂 / 价格筛选全量下线」K4）：**公开侧食堂字典端点已整体删除**——
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

    /**
     * 新增食堂（{@code POST /admin/canteens}）：名称应用层查重（重名 → 400），不加强 DB 唯一索引。
     *
     * @param name 食堂名称（1~64 字）
     * @return 新建的 VO
     * @throws com.bjtufood.common.exception.BusinessException code=400 名称为空 / 超长 / 重名
     */
    CanteenAdminVO createCanteen(String name);

    /**
     * 删除食堂（{@code DELETE /admin/canteens/{id}}）：其下仍有档口时禁止删除（避免孤儿档口）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 食堂不存在 / code=400 其下仍有档口
     */
    void deleteCanteen(Long id);
}
