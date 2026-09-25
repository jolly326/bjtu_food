package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.review.dto.StallAvgRatingVO;
import com.bjtufood.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StallServiceImpl implements StallService {

    /**
     * 「空值语义」的食堂/档口名称集合（§7.23 第 1 条：upsert 时这类名称视为未填，不建档）。
     * 命中即回退 stallId 逻辑，绝不以其为名新建食堂/档口。
     */
    private static final Set<String> EMPTY_NAME_VALUES = Set.of("其他", "其它", "无", "未知");

    /** 新建档口时未提供有效所属食堂的报错文案（与 web 端「食堂必填」契约一致） */
    private static final String MSG_CANTEEN_REQUIRED = "请选择所属食堂";

    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final ReviewMapper reviewMapper;

    @Override
    public List<StallAdminVO> listAllForAdmin() {
        List<Stall> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .orderByAsc(Stall::getCanteenId)
                .orderByAsc(Stall::getSortOrder)
                .orderByDesc(Stall::getUpdatedAt));
        if (stalls.isEmpty()) {
            return List.of();
        }
        // BE-08：一次 IN 查询取回全部档口平均分，替代原 toAdminVO 内逐条 selectAvgRatingByStallId 的 N+1
        Map<Long, BigDecimal> avgRatings = batchAvgRating(stalls);
        return stalls.stream()
                .map(s -> toAdminVO(s, avgRatings.get(s.getId())))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 批量查询档口平均分，构建 stallId → avgRating 映射（BE-08：消除逐档口 N+1）。
     * <p>
     * 复用 {@link ReviewMapper#selectAvgRatingByStallIds}（与 CanteenServiceImpl 同口径）；
     * 无评价的档口不会出现在结果集中，取值时按 0.00 兜底。
     */
    private Map<Long, BigDecimal> batchAvgRating(List<Stall> stalls) {
        List<Long> ids = stalls.stream().map(Stall::getId).distinct().toList();
        Map<Long, BigDecimal> map = new HashMap<>(ids.size());
        for (StallAvgRatingVO r : reviewMapper.selectAvgRatingByStallIds(ids)) {
            if (r.getStallId() != null) {
                map.put(r.getStallId(), r.getAvgRating());
            }
        }
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Stall stall) {
        // canteen_id=0 收口（2026-09-15）：canteenId 显式传入时必须为有效食堂
        // （dish 列表/详情 joinDishSql 对 canteen 为 INNER JOIN，挂 0 的档口菜品会被静默剔除）。
        // null=不修改（MyBatis-Plus updateById NOT_NULL 策略跳过），不校验。
        if (stall.getCanteenId() != null) {
            if (stall.getCanteenId() <= 0 || canteenMapper.selectById(stall.getCanteenId()) == null) {
                throw new BusinessException("请选择所属食堂");
            }
        }
        if (stall.getId() == null || stallMapper.updateById(stall) == 0) {
            throw new BusinessException("Stall not found");
        }
    }

    @Override
    public Long upsertStallByName(String stallName, String rawCanteenName) {
        Stall existing = stallMapper.selectOne(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getName, stallName)
                .last("LIMIT 1"));
        if (existing != null) {
            // 同名档口已存在：直接复用（canteenName 仅在新建档口时消费，不迁移既有档口归属）
            return existing.getId();
        }
        Long canteenId = upsertCanteenIdByName(rawCanteenName);
        if (canteenId == null) {
            // 新建档口必须挂有效食堂：拦截在写入前，杜绝 canteen_id=0 的不可见脏数据
            throw new BusinessException(MSG_CANTEEN_REQUIRED);
        }
        Stall stall = new Stall();
        stall.setName(stallName);
        stall.setCanteenId(canteenId);
        stallMapper.insert(stall);
        return stall.getId();
    }

    @Override
    public boolean existsById(Long stallId) {
        return stallId != null && stallMapper.selectById(stallId) != null;
    }

    /**
     * 按名 upsert 食堂（仅当新建档口时消费）：有效名称查字典命中则复用，未命中自动建档。
     * <p>
     * 空白/「其他」等空值语义名称 <b>不建档也不落 0</b>，返回 null 由调用方 400 拦截
     * （2026-09-15 收口：旧逻辑返回 0L 会产生 canteen_id=0 的档口，其菜品被
     * joinDishSql 的 INNER JOIN 静默剔除，属隐性数据丢失）。
     *
     * @return 食堂 ID；null=无可解析的有效食堂名（调用方必须 400，不得写库）
     */
    private Long upsertCanteenIdByName(String rawCanteenName) {
        String canteenName = normalizeUpsetName(rawCanteenName);
        if (canteenName == null) {
            return null;
        }
        Canteen existing = canteenMapper.selectOne(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getName, canteenName)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }
        Canteen canteen = new Canteen();
        canteen.setName(canteenName);
        canteenMapper.insert(canteen);
        return canteen.getId();
    }

    /**
     * upsert 名称规范化：trim 后为空白或命中 {@link #EMPTY_NAME_VALUES}（「其他」等空值语义）返回 null（不建档）；
     * 其余返回 trim 后的名称。
     */
    private static String normalizeUpsetName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || EMPTY_NAME_VALUES.contains(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private StallAdminVO toAdminVO(Stall stall, BigDecimal avgRating) {
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
        // BE-08：avgRating 由批量 IN 查询一次性取回；无评价（不在结果集）按 0.00 兜底
        vo.setAvgRating((avgRating != null ? avgRating : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        vo.setSortOrder(stall.getSortOrder());
        // 2026-09-15：createdBy 三端零消费（单口令模型无真实身份，写入侧为系统占位值），
        // VO 字段已删除；实体字段与写入侧、stall.created_by 列定义同批退役（阶段4，schema.sql 幂等 DROP）。
        vo.setCreatedAt(stall.getCreatedAt());
        vo.setUpdatedAt(stall.getUpdatedAt());
        return vo;
    }
}
