package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.CanteenAdminVO;

import java.util.List;

/**
 * 食堂服务接口
 * <p>
 * 食堂生命周期 = <b>新增 / 改名 / 删除</b> ＋ 后台列表查询（管理端维护的主数据实体，见
 * docs/func/web/A-主数据维护/A1-食堂管理.md）；**无停业 / 审核能力**。
 * <p>
 * （K4）：**公开侧无食堂字典端点** —— 本接口不提供
 * {@code listCanteens()} / {@code listWithStalls()} 公开查询能力（亦无对应出参 VO）。
 */
public interface CanteenService {

    /**
     * 后台食堂列表
     *
     * @return 后台食堂 VO 列表（含完整图片 URL）
     */
    List<CanteenAdminVO> listAllForAdmin();

    /**
     * 编辑食堂（{@code PUT /admin/canteens/{id}}）：改名走应用层全站查重，重名 → 400。
     * <p>
     * 入参是<b>字段值</b>而非实体（entity）：食堂的可编辑字段只有名称，把实体交给调用方
     * 会让保留列（{@code images} / {@code location} / {@code description} / {@code sort_order}）
     * 与时间列一并暴露在写入面上。
     *
     * @param id   目标食堂 ID（路径参数）
     * @param name 新名称（1~64 字，按 trim 后取值）
     * @throws com.bjtufood.common.exception.BusinessException code=4001 食堂不存在；
     *         code=400 名称为空 / 超长 / 重名
     */
    void update(Long id, String name);

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
