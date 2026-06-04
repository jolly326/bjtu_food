package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.BannerAdminVO;
import com.bjtufood.canteen.entity.Banner;

import java.util.List;

/**
 * 轮播图服务接口
 * <p>
 * 轮播图的后台 CRUD 管理。
 */
public interface BannerService {

    /**
     * 后台轮播图列表（不分页，按 sort_order 升序）
     */
    List<BannerAdminVO> listAll();

    /**
     * 新增轮播图
     */
    void add(Banner banner);

    /**
     * 编辑轮播图
     */
    void update(Banner banner);

    /**
     * 删除轮播图
     */
    void delete(Long id);
}
