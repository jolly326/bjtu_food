package com.bjtufood.banner.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.banner.dto.BannerVO;
import com.bjtufood.banner.entity.Banner;
import com.bjtufood.banner.mapper.BannerMapper;
import com.bjtufood.banner.service.BannerService;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 首页轮播图服务实现（公开只读）
 * <p>
 * 出参收敛：{@code status} / {@code sort_order} 仅用于过滤与排序，**不出参**；
 * {@code image_url} 统一转绝对 URL（与菜品图片同口径：相对路径转绝对、绝对地址原样返回）。
 */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    /** 启用状态值（与 dish.status 同风格：on / off） */
    private static final String STATUS_ON = "on";

    private final BannerMapper bannerMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public List<BannerVO> listBanners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, STATUS_ON)
                        .orderByAsc(Banner::getSortOrder))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    private BannerVO toVO(Banner banner) {
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setImageUrl(imageUrlUtil.toAbsoluteUrl(banner.getImageUrl()));
        return vo;
    }
}
