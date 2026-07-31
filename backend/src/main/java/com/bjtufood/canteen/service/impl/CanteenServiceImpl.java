package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.BannerVO;
import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.CanteenWithStallsVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Banner;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.BannerMapper;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CanteenServiceImpl implements CanteenService {

    private final CanteenMapper canteenMapper;
    private final StallMapper stallMapper;
    private final BannerMapper bannerMapper;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public List<BannerVO> listBanners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, "enabled")
                        .orderByAsc(Banner::getSortOrder))
                .stream()
                .map(banner -> {
                    BannerVO vo = new BannerVO();
                    vo.setTitle(banner.getTitle());
                    vo.setSubtitle(banner.getSubtitle());
                    vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(banner.getImages()));
                    vo.setTargetType(banner.getTargetType());
                    vo.setTargetId(banner.getTargetId());
                    vo.setTargetUrl(banner.getTargetUrl());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CanteenInfoVO> listCanteens() {
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                        .eq(Canteen::getStatus, "open")
                        .orderByAsc(Canteen::getSortOrder))
                .stream()
                .map(canteen -> {
                    CanteenInfoVO vo = new CanteenInfoVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    vo.setLocation(canteen.getLocation());
                    vo.setDescription(canteen.getDescription());
                    vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<String>> listCanteenImages() {
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, "open"));
        Map<String, List<String>> result = new HashMap<>();
        for (Canteen canteen : canteens) {
            result.put(canteen.getName(), imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        }
        return result;
    }

    @Override
    public StallDetailVO getStallDetail(String canteen, String stallName) {
        String canteenName = canteen == null ? "" : canteen.trim();
        String normalizedStallName = stallName == null ? "" : stallName.trim();
        if (!StringUtils.hasText(canteenName) || !StringUtils.hasText(normalizedStallName)) {
            throw new BusinessException("食堂和档口名称不能为空");
        }

        // 先按名称查食堂（仅 open 状态的）
        Canteen canteenEntity = canteenMapper.selectOne(
                new LambdaQueryWrapper<Canteen>()
                        .eq(Canteen::getName, canteenName)
                        .eq(Canteen::getStatus, "open"));
        if (canteenEntity == null) {
            throw new BusinessException("食堂不存在");
        }

        // 按食堂ID和档口名称查档口（仅 open 状态的）
        Stall stall = stallMapper.selectOne(
                new LambdaQueryWrapper<Stall>()
                        .eq(Stall::getCanteenId, canteenEntity.getId())
                        .eq(Stall::getName, normalizedStallName)
                        .eq(Stall::getStatus, "open"));
        if (stall == null) {
            throw new BusinessException("档口不存在");
        }

        return toStallVO(stall);
    }

    @Override
    public List<CanteenWithStallsVO> listWithStalls() {
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                        .eq(Canteen::getStatus, "open")
                        .orderByAsc(Canteen::getSortOrder))
                .stream()
                .map(canteen -> {
                    CanteenWithStallsVO vo = new CanteenWithStallsVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    vo.setLocation(canteen.getLocation());
                    vo.setDescription(canteen.getDescription());
                    vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
                    List<StallDetailVO> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                                    .eq(Stall::getCanteenId, canteen.getId())
                                    .eq(Stall::getStatus, "open")
                                    .orderByAsc(Stall::getSortOrder))
                            .stream()
                            .map(this::toStallVO)
                            .collect(Collectors.toList());
                    vo.setStalls(stalls);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CanteenAdminVO> listAllForAdmin() {
        return canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                        .orderByAsc(Canteen::getSortOrder)
                        .orderByDesc(Canteen::getUpdatedAt))
                .stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
    }

    @Override
    public void add(Canteen canteen) {
        // 创建者：后台录入时记为当前登录用户（学生 UGC 入口接入时同理由前端不可用、后端强制写入）
        canteen.setCreatedBy(SecurityUtil.getCurrentUserId());
        // audit_status 沿用表默认 approved（后台录入默认通过，见 schema.sql 注释）
        canteenMapper.insert(canteen);
    }

    @Override
    public void update(Canteen canteen) {
        if (canteen.getId() == null || canteenMapper.updateById(canteen) == 0) {
            throw new BusinessException("Canteen not found");
        }
    }

    @Override
    public void delete(Long id) {
        Long count = stallMapper.selectCount(new LambdaQueryWrapper<Stall>().eq(Stall::getCanteenId, id));
        if (count > 0) {
            throw new BusinessException("Canteen still has stalls");
        }
        canteenMapper.deleteById(id);
    }

    private StallDetailVO toStallVO(Stall stall) {
        StallDetailVO vo = new StallDetailVO();
        vo.setId(stall.getId());
        vo.setName(stall.getName());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        vo.setLocation(stall.getLocation());
        vo.setDescription(stall.getDescription());
        return vo;
    }

    private CanteenAdminVO toAdminVO(Canteen canteen) {
        CanteenAdminVO vo = new CanteenAdminVO();
        vo.setId(canteen.getId());
        vo.setName(canteen.getName());
        vo.setLocation(canteen.getLocation());
        vo.setDescription(canteen.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        vo.setSortOrder(canteen.getSortOrder());
        vo.setStatus(canteen.getStatus());
        vo.setAuditStatus(canteen.getAuditStatus());
        vo.setRejectReason(canteen.getRejectReason());
        vo.setCreatedBy(canteen.getCreatedBy());
        vo.setCreatedAt(canteen.getCreatedAt());
        vo.setUpdatedAt(canteen.getUpdatedAt());
        return vo;
    }
}
