package com.bjtufood.banner.service;

import com.bjtufood.banner.dto.BannerVO;

import java.util.List;

/**
 * 首页轮播图服务接口（公开只读）
 * <p>
 * 2026-09-22 新增：Banner 由端上静态资源改为后端接口下发（多图轮播）。
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
}
