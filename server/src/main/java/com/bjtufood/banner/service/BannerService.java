package com.bjtufood.banner.service;

import com.bjtufood.banner.dto.BannerAdminVO;
import com.bjtufood.banner.dto.BannerVO;
import com.bjtufood.common.dto.SortItem;

import java.util.List;

/**
 * 首页轮播图服务接口（公开只读）
 * <p>
 * ：Banner 由端上静态资源改为后端接口下发（多图轮播）。
 * 本期不含推荐算法、不含点击跳转、不含管理端写接口。
 */
public interface BannerService {

    /**
     * 首页顶部轮播图列表（公开）
     * <p>
     * 只返回**启用中**（{@code status='on'}）的 Banner，按 {@code sort_order} 升序；
     * **无分页**（运营位数量级极小，非分页返回 {@code List<T>}）；无启用项时返回**空列表**（非 404 / null）。
     *
     * @return 轮播图列表（{@code id} + {@code imageUrl} 绝对 URL）
     */
    List<BannerVO> listBanners();

    // ==================== 管理端写契约（A5，docs/api/web/banners.md） ====================

    /**
     * 管理端 Banner 列表（按 `order` 升序；**含已停用**；不分页）。
     *
     * @return 管理端 VO（图片转绝对 URL）
     */
    List<BannerAdminVO> listAllForAdmin();

    /**
     * 新增 Banner（默认**启用**，排在最后）。
     *
     * @param imageUrl 图片地址（经 `/admin/upload` 取得，**原样入库**）
     * @return 新建的 VO
     * @throws com.bjtufood.common.exception.BusinessException code=400 地址为空 / 超长
     */
    BannerAdminVO create(String imageUrl);

    /**
     * 编辑 Banner（换图；可编辑字段整体替换）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 Banner 不存在 / code=400 地址非法
     */
    void update(Long id, String imageUrl);

    /**
     * 启停（**只改 `status`**，显式传目标状态）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 Banner 不存在 / code=400 status 非法
     */
    void updateStatus(Long id, String status);

    /**
     * 排序（拖拽后**整体提交全量行**；缺行 / 重复 / 未知 id → `400`）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=400 排序提交非法
     */
    void sort(List<SortItem> items);

    /**
     * 删除 Banner。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 Banner 不存在
     */
    void delete(Long id);
}
