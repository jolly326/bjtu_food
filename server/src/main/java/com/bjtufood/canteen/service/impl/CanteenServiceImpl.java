package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.CanteenAdminVO;
import com.bjtufood.canteen.dto.CanteenInfoVO;
import com.bjtufood.canteen.dto.CanteenWithStallsVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.CanteenService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ImageUrlUtil imageUrlUtil;
    private final ReviewMapper reviewMapper;
    // 注：DishMapper 依赖已随 dishCount/topDishes/perCapita 下线一并移除（本类不再查询菜品）。

    @Override
    public List<CanteenInfoVO> listCanteens() {
        // 注：本接口不接收 lat/lng——坐标随食堂 VO 返回，距离由前端本地 Haversine 算（用户位置不出本机）
        // 食堂已去实体化（2026-09-14）：无停业语义，不再按 status 过滤，全量字典按 sort_order 返回
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
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
    public List<CanteenWithStallsVO> listWithStalls() {
        // 食堂/档口已去实体化（2026-09-14）：无停业语义，不再按 status 过滤，全量字典按 sort_order 返回
        List<Canteen> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                .orderByAsc(Canteen::getSortOrder));
        // 收集所有档口 ID，批量查询平均分一次，消除逐档口 N+1 查询
        List<Stall> allStalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .in(Stall::getCanteenId, canteens.stream().map(Canteen::getId).toList())
                .orderByAsc(Stall::getSortOrder));
        Map<Long, BigDecimal> avgRatingMap = batchAvgRating(allStalls);
        // 2026-09-15：原「批量查询全部档口在售菜品」（dishCount/topDishes/perCapita 白算的数据源）
        // 随三字段下线一并删除——/canteens/all 少一次全量菜品 IN 查询。
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
                            .map(s -> toStallVO(s, avgRatingMap))
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
    @Transactional(rollbackFor = Exception.class)
    public void update(Canteen canteen) {
        if (canteen.getId() == null || canteenMapper.updateById(canteen) == 0) {
            throw new BusinessException("Canteen not found");
        }
    }

    private StallDetailVO toStallVO(Stall stall, Map<Long, BigDecimal> avgRatingMap) {
        StallDetailVO vo = new StallDetailVO();
        vo.setId(stall.getId());
        vo.setName(stall.getName());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        vo.setLocation(stall.getLocation());
        // 楼层/窗口号（端上有消费：档口卡展示位置）。营业时间字段已于 2026-09-14 §7.14 D 整体下线，
        // 此前该值本就未填充（恒为 null），故删除实体/VO 字段不改变本接口对外语义。
        vo.setFloor(stall.getFloor());
        vo.setWindowNo(stall.getWindowNo());
        vo.setDescription(stall.getDescription());
        BigDecimal avg = avgRatingMap.get(stall.getId());
        vo.setAvgRating(avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
        // 2026-09-15：dishCount / topDishes / perCapita 已随三端零消费下线（字段与派生逻辑同批删除）
        return vo;
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
        List<com.bjtufood.review.dto.StallAvgRatingVO> ratings = reviewMapper.selectAvgRatingByStallIds(ids);
        Map<Long, BigDecimal> map = new HashMap<>(ratings.size());
        for (com.bjtufood.review.dto.StallAvgRatingVO r : ratings) {
            map.put(r.getStallId(), r.getAvgRating());
        }
        return map;
    }

    private CanteenAdminVO toAdminVO(Canteen canteen) {
        CanteenAdminVO vo = new CanteenAdminVO();
        vo.setId(canteen.getId());
        vo.setName(canteen.getName());
        vo.setLocation(canteen.getLocation());
        vo.setDescription(canteen.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        vo.setSortOrder(canteen.getSortOrder());
        // 2026-09-15：createdBy 三端零消费（单口令模型无真实身份，恒为系统占位值），
        // 已随 VO 字段一并删除；实体 canteen.created_by 列保留（写入侧仍在用，仅收敛对外暴露）。
        vo.setCreatedAt(canteen.getCreatedAt());
        vo.setUpdatedAt(canteen.getUpdatedAt());
        return vo;
    }
}
