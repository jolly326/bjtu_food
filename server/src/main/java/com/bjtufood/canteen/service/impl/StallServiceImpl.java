package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StallServiceImpl implements StallService {

    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final ReviewMapper reviewMapper;

    @Override
    public List<StallAdminVO> listAllForAdmin() {
        return stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .orderByAsc(Stall::getCanteenId)
                        .orderByAsc(Stall::getSortOrder)
                        .orderByDesc(Stall::getUpdatedAt))
                .stream()
                .map(this::toAdminVO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Stall stall) {
        if (canteenMapper.selectById(stall.getCanteenId()) == null) {
            throw new BusinessException("Canteen not found");
        }
        // 创建者：后台录入时记为当前登录用户，禁止前端传入
        stall.setCreatedBy(SecurityUtil.getCurrentUserId());
        stallMapper.insert(stall);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Stall stall) {
        if (stall.getId() == null || stallMapper.updateById(stall) == 0) {
            throw new BusinessException("Stall not found");
        }
    }

    private StallAdminVO toAdminVO(Stall stall) {
        StallAdminVO vo = new StallAdminVO();
        vo.setId(stall.getId());
        vo.setCanteenId(stall.getCanteenId());
        vo.setName(stall.getName());
        vo.setLocation(stall.getLocation());
        // 楼层/窗口号（端上有消费：档口卡展示位置）。营业时间字段已于 2026-09-14 §7.14 D 整体下线，
        // 此前该值本就未填充（恒为 null），故删除实体/VO 字段不影响后台接口对外语义。
        vo.setFloor(stall.getFloor());
        vo.setWindowNo(stall.getWindowNo());
        vo.setDescription(stall.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        // 档口评分统一实时聚合（BCNF：stall.avg_rating 孤岛字段已删，与 toVO 同口径，避免两端不一致）
        BigDecimal avg = reviewMapper.selectAvgRatingByStallId(stall.getId());
        vo.setAvgRating(avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
        vo.setSortOrder(stall.getSortOrder());
        vo.setCreatedBy(stall.getCreatedBy());
        vo.setCreatedAt(stall.getCreatedAt());
        vo.setUpdatedAt(stall.getUpdatedAt());
        return vo;
    }
}
