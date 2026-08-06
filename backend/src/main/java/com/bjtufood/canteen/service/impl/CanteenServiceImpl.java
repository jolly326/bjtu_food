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
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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
    private final ReviewMapper reviewMapper;
    private final DishMapper dishMapper;

    @Override
    public List<BannerVO> listBanners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, "enabled")
                        .orderByAsc(Banner::getSortOrder))
                .stream()
                .map(banner -> {
                    BannerVO vo = new BannerVO();
                    vo.setId(banner.getId());
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
        return listCanteens(null, null);
    }

    @Override
    public List<CanteenInfoVO> listCanteens(BigDecimal lat, BigDecimal lng) {
        boolean byDistance = lat != null && lng != null;
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, "open")
                .orderByAsc(Canteen::getSortOrder));
        return canteens.stream()
                .map(canteen -> {
                    CanteenInfoVO vo = new CanteenInfoVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    vo.setLocation(canteen.getLocation());
                    vo.setDescription(canteen.getDescription());
                    vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
                    // 距离（米）：用户位置到食堂坐标的 haversine 直线距离；无坐标食堂置 null 排最后
                    if (byDistance && canteen.getLatitude() != null && canteen.getLongitude() != null) {
                        vo.setDistance(distanceMeters(lat, lng, canteen.getLatitude(), canteen.getLongitude()));
                    }
                    return vo;
                })
                .sorted(byDistance
                        ? java.util.Comparator.comparing(
                                (CanteenInfoVO v) -> v.getDistance(),
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        : java.util.Comparator.comparingLong(v -> (v.getId() == null ? 0L : v.getId())))
                .collect(Collectors.toList());
    }

    /** haversine 距离（米）：两经纬度点的球面直线距离 */
    private Integer distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(R * c);
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
        BigDecimal avg = reviewMapper.selectAvgRatingByStallId(stall.getId());
        vo.setAvgRating(avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
        // 主要菜品（评分前3）与菜品数（task todo#3：档口卡展示）；dish.status 用 'on' 表示在售
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStallId, stall.getId())
                .eq(Dish::getStatus, "on")
                .orderByDesc(Dish::getAvgRating)
                .orderByDesc(Dish::getUpdatedAt));
        vo.setDishCount(dishes.size());
        vo.setTopDishes(dishes.stream().limit(3).map(Dish::getName).toList());
        // 人均消费（元，展示用）：在售菜品成交价（分：有促销价取 promoPrice，否则取 price）中位数 → /100 转元取整
        vo.setPerCapita(derivePerCapita(dishes));
        return vo;
    }

    /**
     * 派生档口人均消费（元，展示用，返回整元）。
     * 取该档口在售菜品成交价（分）的中位数，转元取整；无在售菜品时返回 null。
     */
    private Integer derivePerCapita(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return null;
        }
        List<Integer> prices = dishes.stream()
                .map(d -> d.getPromoPrice() != null ? d.getPromoPrice() : d.getPrice())
                .filter(p -> p != null && p > 0)
                .sorted()
                .toList();
        if (prices.isEmpty()) {
            return null;
        }
        int mid = prices.size() / 2;
        int medianFen = (prices.size() % 2 == 1)
                ? prices.get(mid)
                : (prices.get(mid - 1) + prices.get(mid)) / 2;
        return medianFen / 100;
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
