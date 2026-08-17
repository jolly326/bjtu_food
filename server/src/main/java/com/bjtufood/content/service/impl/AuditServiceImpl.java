package com.bjtufood.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.content.dto.AuditVO;
import com.bjtufood.content.service.AuditService;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.moment.entity.Moment;
import com.bjtufood.moment.mapper.MomentMapper;
import com.bjtufood.notify.constant.NotificationConst;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UGC 审核服务实现
 * <p>
 * 复用 dish / stall / canteen 三张表的 Mapper，按 type 路由；审核通过置 approved、退回置 rejected 并写原因。
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final MomentMapper momentMapper;
    private final UserMapper userMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final NotificationService notificationService;

    @Override
    public IPage<AuditVO> listAudit(String type, String status, int page, int pageSize) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        Page<Object> pageObj = new Page<>(page, pageSize);
        List<AuditVO> records;
        long total;

        if ("dish".equals(type)) {
            LambdaQueryWrapper<Dish> w = new LambdaQueryWrapper<Dish>().orderByDesc(Dish::getCreatedAt);
            if (StringUtils.hasText(status)) w.eq(Dish::getAuditStatus, status);
            IPage<Dish> p = dishMapper.selectPage(new Page<>(page, pageSize), w);
            total = p.getTotal();
            records = p.getRecords().stream().map(this::toDishVO).toList();
        } else if ("stall".equals(type)) {
            LambdaQueryWrapper<Stall> w = new LambdaQueryWrapper<Stall>().orderByDesc(Stall::getCreatedAt);
            if (StringUtils.hasText(status)) w.eq(Stall::getAuditStatus, status);
            IPage<Stall> p = stallMapper.selectPage(new Page<>(page, pageSize), w);
            total = p.getTotal();
            records = p.getRecords().stream().map(this::toStallVO).toList();
        } else if ("canteen".equals(type)) {
            LambdaQueryWrapper<Canteen> w = new LambdaQueryWrapper<Canteen>().orderByDesc(Canteen::getCreatedAt);
            if (StringUtils.hasText(status)) w.eq(Canteen::getAuditStatus, status);
            IPage<Canteen> p = canteenMapper.selectPage(new Page<>(page, pageSize), w);
            total = p.getTotal();
            records = p.getRecords().stream().map(this::toCanteenVO).toList();
        } else if ("moment".equals(type)) {
            LambdaQueryWrapper<Moment> w = new LambdaQueryWrapper<Moment>().orderByDesc(Moment::getCreatedAt);
            if (StringUtils.hasText(status)) w.eq(Moment::getAuditStatus, status);
            IPage<Moment> p = momentMapper.selectPage(new Page<>(page, pageSize), w);
            total = p.getTotal();
            records = p.getRecords().stream().map(this::toMomentVO).toList();
        } else {
            throw new BusinessException("未知的审核类型：" + type);
        }

        // 批量补齐提交人昵称
        // 注意：当所有记录的 createdBy 均为 null（如种子数据）时，ids 为空集合，
        // 若直接 .in(ids) 会生成非法 SQL "IN ()" 触发 500，需先做空集合防护；
        // 同时 Map.of() 是不可变空 Map，对其调用 get(null) 会抛 NPE
        // （ImmutableCollections$MapN.get 内部对 key 做了 Objects.requireNonNull），
        // 故此处必须用可变 HashMap 以容忍 null key。
        List<Long> submitterIds = records.stream()
                .map(AuditVO::getCreatedBy)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> userMap = new HashMap<>();
        if (!submitterIds.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, submitterIds))
                    .forEach(u -> userMap.put(u.getId(), u.getNickname()));
        }
        records.forEach(v -> v.setSubmitterName(userMap.get(v.getCreatedBy())));

        IPage<AuditVO> result = new Page<>(page, pageSize, total);
        result.setRecords(records);
        return result;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void approve(String type, Long id) {
        if ("dish".equals(type)) {
            Dish e = dishMapper.selectById(id);
            if (e == null) throw new BusinessException("菜品不存在");
            e.setAuditStatus("approved");
            e.setRejectReason(null);
            dishMapper.updateById(e);
            sendDishAuditNotification(e, true, null);
        } else if ("stall".equals(type)) {
            Stall e = stallMapper.selectById(id);
            if (e == null) throw new BusinessException("档口不存在");
            e.setAuditStatus("approved");
            e.setRejectReason(null);
            stallMapper.updateById(e);
        } else if ("canteen".equals(type)) {
            Canteen e = canteenMapper.selectById(id);
            if (e == null) throw new BusinessException("食堂不存在");
            e.setAuditStatus("approved");
            e.setRejectReason(null);
            canteenMapper.updateById(e);
        } else if ("moment".equals(type)) {
            Moment e = momentMapper.selectById(id);
            if (e == null) throw new BusinessException("动态不存在");
            e.setAuditStatus("approved");
            e.setRejectReason(null);
            momentMapper.updateById(e);
            sendMomentAuditNotification(e, true, null);
        } else {
            throw new BusinessException("未知的审核类型：" + type);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void reject(String type, Long id, String rejectReason) {
        if (!StringUtils.hasText(rejectReason)) {
            throw new BusinessException("退回原因不能为空");
        }
        if ("dish".equals(type)) {
            Dish e = dishMapper.selectById(id);
            if (e == null) throw new BusinessException("菜品不存在");
            e.setAuditStatus("rejected");
            e.setRejectReason(rejectReason);
            dishMapper.updateById(e);
            sendDishAuditNotification(e, false, rejectReason);
        } else if ("stall".equals(type)) {
            Stall e = stallMapper.selectById(id);
            if (e == null) throw new BusinessException("档口不存在");
            e.setAuditStatus("rejected");
            e.setRejectReason(rejectReason);
            stallMapper.updateById(e);
        } else if ("canteen".equals(type)) {
            Canteen e = canteenMapper.selectById(id);
            if (e == null) throw new BusinessException("食堂不存在");
            e.setAuditStatus("rejected");
            e.setRejectReason(rejectReason);
            canteenMapper.updateById(e);
        } else if ("moment".equals(type)) {
            Moment e = momentMapper.selectById(id);
            if (e == null) throw new BusinessException("动态不存在");
            e.setAuditStatus("rejected");
            e.setRejectReason(rejectReason);
            momentMapper.updateById(e);
            sendMomentAuditNotification(e, false, rejectReason);
        } else {
            throw new BusinessException("未知的审核类型：" + type);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void batchApprove(String type, List<Long> ids) {
        if (ids != null) ids.forEach(id -> approve(type, id));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void batchReject(String type, List<Long> ids, String rejectReason) {
        if (!StringUtils.hasText(rejectReason)) {
            throw new BusinessException("退回原因不能为空");
        }
        if (ids != null) ids.forEach(id -> reject(type, id, rejectReason));
    }

    private AuditVO toDishVO(Dish d) {
        AuditVO v = new AuditVO();
        v.setId(d.getId());
        v.setType("dish");
        v.setName(d.getName());
        v.setPrice(d.getPrice());
        v.setDescription(d.getDescription());
        v.setImages(imageUrlUtil.parseAndToAbsoluteUrls(d.getImages()));
        v.setCanteenId(resolveCanteenIdByStall(d.getStallId()));
        v.setStallId(d.getStallId());
        v.setAuditStatus(d.getAuditStatus());
        v.setRejectReason(d.getRejectReason());
        v.setCreatedBy(d.getCreatedBy());
        v.setCreatedAt(d.getCreatedAt());
        return v;
    }

    private AuditVO toStallVO(Stall s) {
        AuditVO v = new AuditVO();
        v.setId(s.getId());
        v.setType("stall");
        v.setName(s.getName());
        v.setLocation(s.getLocation());
        v.setDescription(s.getDescription());
        v.setImages(imageUrlUtil.parseAndToAbsoluteUrls(s.getImages()));
        v.setCanteenId(s.getCanteenId());
        v.setAuditStatus(s.getAuditStatus());
        v.setRejectReason(s.getRejectReason());
        v.setCreatedBy(s.getCreatedBy());
        v.setCreatedAt(s.getCreatedAt());
        return v;
    }

    private AuditVO toCanteenVO(Canteen c) {
        AuditVO v = new AuditVO();
        v.setId(c.getId());
        v.setType("canteen");
        v.setName(c.getName());
        v.setLocation(c.getLocation());
        v.setDescription(c.getDescription());
        v.setImages(imageUrlUtil.parseAndToAbsoluteUrls(c.getImages()));
        v.setAuditStatus(c.getAuditStatus());
        v.setRejectReason(c.getRejectReason());
        v.setCreatedBy(c.getCreatedBy());
        v.setCreatedAt(c.getCreatedAt());
        return v;
    }

    private AuditVO toMomentVO(Moment m) {
        AuditVO v = new AuditVO();
        v.setId(m.getId());
        v.setType("moment");
        v.setName(truncate(m.getContent(), 30));
        v.setDescription(m.getContent());
        v.setImages(imageUrlUtil.parseAndToAbsoluteUrls(m.getImages()));
        v.setAuditStatus(m.getAuditStatus());
        v.setRejectReason(m.getRejectReason());
        v.setCreatedBy(m.getUserId());
        v.setCreatedAt(m.getCreatedAt());
        return v;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void sendMomentAuditNotification(Moment m, boolean approved, String rejectReason) {
        Notification n = new Notification();
        n.setUserId(m.getUserId());
        n.setType(NotificationConst.TYPE_MOMENT_AUDIT);
        n.setRelatedId(m.getId());
        n.setIsRead(0);
        if (approved) {
            n.setTitle("动态审核通过");
            n.setContent("您发布的动态已通过审核，现在对外可见啦~");
        } else {
            n.setTitle("动态审核未通过");
            n.setContent("您的动态未通过审核：" + (rejectReason == null ? "" : rejectReason));
        }
        notificationService.notify(n);
    }

    private void sendDishAuditNotification(Dish d, boolean approved, String rejectReason) {
        if (d.getCreatedBy() == null) return;
        Notification n = new Notification();
        n.setUserId(d.getCreatedBy());
        n.setType(NotificationConst.TYPE_DISH_AUDIT);
        n.setRelatedId(d.getId());
        n.setIsRead(0);
        if (approved) {
            n.setTitle("菜品审核通过");
            n.setContent("您提交的菜品「" + d.getName() + "」已通过审核，现在对外可见啦~");
        } else {
            n.setTitle("菜品审核未通过");
            n.setContent("您提交的菜品「" + d.getName() + "」未通过审核：" + (rejectReason == null ? "" : rejectReason));
        }
        notificationService.notify(n);
    }

    private Long resolveCanteenIdByStall(Long stallId) {
        if (stallId == null) return null;
        Stall s = stallMapper.selectById(stallId);
        return s != null ? s.getCanteenId() : null;
    }
}
