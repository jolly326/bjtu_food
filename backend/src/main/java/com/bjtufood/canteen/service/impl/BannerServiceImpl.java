package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.BannerAdminVO;
import com.bjtufood.canteen.entity.Banner;
import com.bjtufood.canteen.mapper.BannerMapper;
import com.bjtufood.canteen.service.BannerService;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public List<BannerAdminVO> listAll() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .orderByAsc(Banner::getSortOrder))
                .stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
    }

    @Override
    public void add(Banner banner) {
        bannerMapper.insert(banner);
    }

    @Override
    public void update(Banner banner) {
        if (banner.getId() == null || bannerMapper.updateById(banner) == 0) {
            throw new com.bjtufood.common.exception.BusinessException("轮播图不存在");
        }
    }

    @Override
    public void delete(Long id) {
        bannerMapper.deleteById(id);
    }

    private BannerAdminVO toAdminVO(Banner banner) {
        BannerAdminVO vo = new BannerAdminVO();
        vo.setId(banner.getId());
        vo.setTitle(banner.getTitle());
        vo.setSubtitle(banner.getSubtitle());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(banner.getImages()));
        vo.setType(banner.getType());
        vo.setTargetId(banner.getTargetId());
        vo.setTargetUrl(banner.getTargetUrl());
        vo.setCanteenId(banner.getCanteenId());
        vo.setSortOrder(banner.getSortOrder());
        vo.setStatus(banner.getStatus());
        vo.setCreatedAt(banner.getCreatedAt());
        vo.setUpdatedAt(banner.getUpdatedAt());
        return vo;
    }
}
