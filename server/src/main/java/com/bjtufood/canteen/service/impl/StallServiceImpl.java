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
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StallServiceImpl implements StallService {

    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final DishMapper dishMapper;
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
    public void add(Stall stall) {
        if (canteenMapper.selectById(stall.getCanteenId()) == null) {
            throw new BusinessException("Canteen not found");
        }
        // 创建者：后台录入时记为当前登录用户
        stall.setCreatedBy(SecurityUtil.getCurrentUserId());
        // audit_status 沿用表默认 approved（后台录入默认通过，见 schema.sql 注释）
        stallMapper.insert(stall);
    }

    @Override
    public void update(Stall stall) {
        if (stall.getId() == null || stallMapper.updateById(stall) == 0) {
            throw new BusinessException("Stall not found");
        }
    }

    @Override
    public void delete(Long id) {
        Long count = dishMapper.selectCount(new LambdaQueryWrapper<Dish>().eq(Dish::getStallId, id));
        if (count > 0) {
            throw new BusinessException("Stall still has dishes");
        }
        stallMapper.deleteById(id);
    }

    @Override
    public Stall getById(Long id) {
        Stall stall = stallMapper.selectById(id);
        if (stall == null) {
            throw new BusinessException("Stall not found");
        }
        return stall;
    }

    private StallAdminVO toAdminVO(Stall stall) {
        StallAdminVO vo = new StallAdminVO();
        vo.setId(stall.getId());
        vo.setCanteenId(stall.getCanteenId());
        vo.setName(stall.getName());
        vo.setLocation(stall.getLocation());
        vo.setDescription(stall.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        // 档口评分统一实时聚合（BCNF：stall.avg_rating 孤岛字段已删，与 toVO 同口径，避免两端不一致）
        BigDecimal avg = reviewMapper.selectAvgRatingByStallId(stall.getId());
        vo.setAvgRating(avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
        vo.setSortOrder(stall.getSortOrder());
        vo.setStatus(stall.getStatus());
        vo.setAuditStatus(stall.getAuditStatus());
        vo.setRejectReason(stall.getRejectReason());
        vo.setCreatedBy(stall.getCreatedBy());
        vo.setCreatedAt(stall.getCreatedAt());
        vo.setUpdatedAt(stall.getUpdatedAt());
        return vo;
    }
}
