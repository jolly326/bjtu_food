package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.BannerAdminVO;
import com.bjtufood.canteen.entity.Banner;
import com.bjtufood.canteen.mapper.BannerMapper;
import com.bjtufood.canteen.service.BannerService;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        validateTargetType(banner);
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

    /**
     * 校验 target_type 与跳转目标一致性（spec §3.x.2）
     * <p>
     * DISH / ACTIVITY：必填 target_id，target_url 置空；
     * URL：必填 target_url，target_id 置空；
     * NONE：target_id / target_url 均清空；
     * 不合法类型或目标缺失 → 抛 BusinessException(400)。
     */
    private void validateTargetType(Banner banner) {
        String targetType = banner.getTargetType();
        if (!StringUtils.hasText(targetType)) {
            targetType = "DISH"; // 缺省兼容历史 dish 类型
            banner.setTargetType(targetType);
        }
        targetType = targetType.toUpperCase();
        banner.setTargetType(targetType);
        switch (targetType) {
            case "DISH" -> {
                if (banner.getTargetId() == null) {
                    throw new com.bjtufood.common.exception.BusinessException(targetType + " 类型必须填写 targetId");
                }
                banner.setTargetUrl(null);
                // 同步历史 type 字段，保持兼容
                banner.setType("dish");
            }
            case "ACTIVITY" -> throw new com.bjtufood.common.exception.BusinessException(
                    "ACTIVITY 类型已废弃，活动统一经 Banner 的 URL 外链承载（task-12.10）");
            case "URL" -> {
                if (!StringUtils.hasText(banner.getTargetUrl())) {
                    throw new com.bjtufood.common.exception.BusinessException("URL 类型必须填写 targetUrl");
                }
                banner.setTargetId(null);
                banner.setType("url");
            }
            case "NONE" -> {
                banner.setTargetId(null);
                banner.setTargetUrl(null);
                banner.setType("url");
            }
            default -> throw new com.bjtufood.common.exception.BusinessException("非法的 target_type：" + targetType);
        }
    }

    private BannerAdminVO toAdminVO(Banner banner) {
        BannerAdminVO vo = new BannerAdminVO();
        vo.setId(banner.getId());
        vo.setTitle(banner.getTitle());
        vo.setSubtitle(banner.getSubtitle());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(banner.getImages()));
        vo.setType(banner.getType());
        vo.setTargetType(banner.getTargetType());
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
