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
        // 注：lat/lng 不再用于服务端距离计算——坐标随食堂返回，距离由前端本地 Haversine 算（用户位置不出本机）
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
                    // 仅暴露坐标，距离交给前端本地算
                    vo.setLatitude(canteen.getLatitude());
                    vo.setLongitude(canteen.getLongitude());
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

        Map<Long, List<Dish>> dishesByStall = batchOnSaleDishesByStall(List.of(stall));
        return toStallVO(stall, batchAvgRating(List.of(stall)), dishesByStall.getOrDefault(stall.getId(), List.of()));
    }

    @Override
    public List<CanteenWithStallsVO> listWithStalls() {
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getStatus, "open")
                .orderByAsc(Canteen::getSortOrder));
        // 收集所有档口 ID，批量查询平均分一次，消除逐档口 N+1 查询
        List<Stall> allStalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .in(Stall::getCanteenId, canteens.stream().map(Canteen::getId).toList())
                .eq(Stall::getStatus, "open")
                .orderByAsc(Stall::getSortOrder));
        Map<Long, BigDecimal> avgRatingMap = batchAvgRating(allStalls);
        // 批量查询全部档口在售菜品（一次 IN 查询按 stallId 分组），消除逐档口 N+1
        Map<Long, List<Dish>> dishesByStall = batchOnSaleDishesByStall(allStalls);
        return canteens.stream()
                .map(canteen -> {
                    CanteenWithStallsVO vo = new CanteenWithStallsVO();
                    vo.setId(canteen.getId());
                    vo.setName(canteen.getName());
                    vo.setLocation(canteen.getLocation());
                    vo.setDescription(canteen.getDescription());
                    vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
                    List<StallDetailVO> stalls = allStalls.stream()
                            .filter(s -> s.getCanteenId().equals(canteen.getId()))
                            .map(s -> toStallVO(s, avgRatingMap, dishesByStall.getOrDefault(s.getId(), List.of())))
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

    private StallDetailVO toStallVO(Stall stall, Map<Long, BigDecimal> avgRatingMap, List<Dish> onSaleDishes) {
        StallDetailVO vo = new StallDetailVO();
        vo.setId(stall.getId());
        vo.setName(stall.getName());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        vo.setLocation(stall.getLocation());
        vo.setDescription(stall.getDescription());
        BigDecimal avg = avgRatingMap.get(stall.getId());
        vo.setAvgRating(avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
        // 主要菜品（评分前3）与菜品数（task todo#3：档口卡展示）；onSaleDishes 已按 avg_rating/updated_at 降序
        vo.setDishCount(onSaleDishes.size());
        vo.setTopDishes(onSaleDishes.stream().limit(3).map(Dish::getName).toList());
        // 人均消费（元，展示用）：在售菜品成交价（分：有促销价取 promoPrice，否则取 price）中位数 → /100 转元取整
        vo.setPerCapita(derivePerCapita(onSaleDishes));
        return vo;
    }

    /**
     * 批量查询档口在售菜品，构建 stallId → 在售菜品列表（已按评分/更新时间降序）的映射
     * （消除逐档口 N+1；空档口集合返回空 Map）。
     */
    private Map<Long, List<Dish>> batchOnSaleDishesByStall(List<Stall> stalls) {
        if (stalls == null || stalls.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = stalls.stream().map(Stall::getId).distinct().toList();
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .in(Dish::getStallId, ids)
                        .eq(Dish::getStatus, com.bjtufood.dish.constant.DishConst.STATUS_ON)
                        .orderByDesc(Dish::getAvgRating)
                        .orderByDesc(Dish::getUpdatedAt))
                .stream()
                .collect(Collectors.groupingBy(Dish::getStallId));
    }

    /**
     * 批量查询档口平均分，构建 stallId → avgRating 的映射（消除逐档口 N+1 查询）。
     * <p>
     * 复杂度：原 O(N) 次 DB 调用降为 1 次 IN 查询；空档口集合返回空 Map。
     */
    private Map<Long, BigDecimal> batchAvgRating(List<Stall> stalls) {
        if (stalls == null || stalls.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = stalls.stream().map(Stall::getId).distinct().toList();
        List<com.bjtufood.review.dto.StallAvgRatingDTO> ratings = reviewMapper.selectAvgRatingByStallIds(ids);
        Map<Long, BigDecimal> map = new HashMap<>(ratings.size());
        for (com.bjtufood.review.dto.StallAvgRatingDTO r : ratings) {
            map.put(r.getStallId(), r.getAvgRating());
        }
        return map;
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
