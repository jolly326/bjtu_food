package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.CanteenWithStallsVO;
import com.bjtufood.canteen.entity.Canteen;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 食堂服务接口
 * <p>
 * 食堂和档口的查询与管理，食堂管理员只能查看，系统管理员可增删改。
 */
public interface CanteenService {

    /**
     * 获取食堂列表（首页用于展示）
     * <p>
     * 查询状态为 open 的食堂，按 sort_order 排序
     *
     * @return 食堂展示列表
     */
    List<CanteenInfoVO> listCanteens();

    /**
     * 获取食堂列表（首页推荐，支持按距离排序）
     * <p>
     * 传 lat/lng 时按用户位置到食堂的直线距离（haversine，单位米）升序排序；
     * 不传时保持 sort_order 排序。
     *
     * @param lat 用户纬度（GCJ-02，可选）
     * @param lng 用户经度（GCJ-02，可选）
     * @return 食堂展示列表（含 distance 字段，米）
     */
    List<CanteenInfoVO> listCanteens(BigDecimal lat, BigDecimal lng);

    /**
     * 获取食堂背景图片映射
     * <p>
     * key = 食堂名称, value = 食堂图片 URL
     *
     * @return 食堂名称到图片的映射
     */
    Map<String, List<String>> listCanteenImages();

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
