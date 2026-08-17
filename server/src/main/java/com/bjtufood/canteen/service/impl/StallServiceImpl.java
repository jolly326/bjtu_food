package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.MyPublishStallVO;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.dto.StallDetailVO;
import com.bjtufood.canteen.entity.Canteen;
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
    public List<StallDetailVO> listByCanteenId(Long canteenId) {
        return stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, canteenId)
                .eq(Stall::getStatus, "open")
                .orderByAsc(Stall::getSortOrder))
                .stream()
                .map(this::toVO)
                .toList();
    }

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

    @Override
    public Long submitUgc(com.bjtufood.canteen.dto.StallUgcSubmitReq req) {
        String type = req.getType() == null ? "" : req.getType().trim().toLowerCase();
        Long userId = SecurityUtil.getCurrentUserId();

        if ("stall".equals(type)) {
            if (req.getCanteenId() == null) {
                throw new BusinessException("提交档口时必须关联食堂(canteenId)");
            }
            if (canteenMapper.selectById(req.getCanteenId()) == null) {
                throw new BusinessException("Canteen not found");
            }
            if (req.getName() == null || req.getName().isBlank()) {
                throw new BusinessException("档口名称不能为空");
            }
            Stall stall = new Stall();
            stall.setCanteenId(req.getCanteenId());
            stall.setName(req.getName().trim());
            stall.setDescription(req.getDescription());
            stall.setLocation(req.getLocation());
            stall.setStatus("closed");          // UGC 未审核，不对外展示
            stall.setAuditStatus("pending");    // 待审核
            stall.setCreatedBy(userId);
            stallMapper.insert(stall);
            return stall.getId();
        } else if ("canteen".equals(type)) {
            if (req.getName() == null || req.getName().isBlank()) {
                throw new BusinessException("食堂名称不能为空");
            }
            Canteen canteen = new Canteen();
            canteen.setName(req.getName().trim());
            canteen.setDescription(req.getDescription());
            canteen.setLocation(req.getLocation());
            canteen.setStatus("closed");        // UGC 未审核，不对外展示
            canteen.setAuditStatus("pending");  // 待审核
            canteen.setCreatedBy(userId);
            canteenMapper.insert(canteen);
            return canteen.getId();
        } else {
            throw new BusinessException("type 仅支持 stall 或 canteen");
        }
    }

    @Override
    public List<MyPublishStallVO> listMySubmissions() {
        Long userId = SecurityUtil.getCurrentUserId();

        List<MyPublishStallVO> canteens = canteenMapper.selectList(new LambdaQueryWrapper<Canteen>()
                        .eq(Canteen::getCreatedBy, userId)
                        .orderByDesc(Canteen::getCreatedAt))
                .stream()
                .map(this::toMyPublishVO)
                .toList();

        List<MyPublishStallVO> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .eq(Stall::getCreatedBy, userId)
                        .orderByDesc(Stall::getCreatedAt))
                .stream()
                .map(this::toMyPublishVO)
                .toList();

        // 顺序：先食堂后档口，均为创建时间倒序
        List<MyPublishStallVO> result = new java.util.ArrayList<>(canteens.size() + stalls.size());
        result.addAll(canteens);
        result.addAll(stalls);
        return result;
    }

    private StallDetailVO toVO(Stall stall) {
        StallDetailVO vo = new StallDetailVO();
        vo.setId(stall.getId());
        vo.setName(stall.getName());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        vo.setLocation(stall.getLocation());
        vo.setDescription(stall.getDescription());
        BigDecimal avg = reviewMapper.selectAvgRatingByStallId(stall.getId());
        vo.setAvgRating(avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
        return vo;
    }

    private MyPublishStallVO toMyPublishVO(Stall stall) {
        MyPublishStallVO vo = new MyPublishStallVO();
        vo.setId(stall.getId());
        vo.setCanteenId(stall.getCanteenId());
        vo.setName(stall.getName());
        vo.setLocation(stall.getLocation());
        vo.setDescription(stall.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        vo.setAuditStatus(stall.getAuditStatus());
        vo.setRejectReason(stall.getRejectReason());
        vo.setCreatedAt(stall.getCreatedAt());
        return vo;
    }

    private MyPublishStallVO toMyPublishVO(Canteen canteen) {
        MyPublishStallVO vo = new MyPublishStallVO();
        vo.setId(canteen.getId());
        vo.setCanteenId(null);
        vo.setName(canteen.getName());
        vo.setLocation(canteen.getLocation());
        vo.setDescription(canteen.getDescription());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(canteen.getImages()));
        vo.setAuditStatus(canteen.getAuditStatus());
        vo.setRejectReason(canteen.getRejectReason());
        vo.setCreatedAt(canteen.getCreatedAt());
        return vo;
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
