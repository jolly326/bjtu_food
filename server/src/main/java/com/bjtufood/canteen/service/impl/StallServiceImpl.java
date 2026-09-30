package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.dto.StallBriefVO;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
    // 2026-09-28 环偿还：此前注入 ReviewQueryService 以填充后台列表的档口均分，导致
    //   canteen -> review -> dish -> canteen 形成包级循环依赖（dish 需 canteen 的档口名）。
    //   档口均分是 review 域按 dish 聚合出的**派生展示值**，不属于 canteen 的自有知识；
    //   由 canteen 主动拉取等于让「属性字典」反向依赖「评价」，方向本就颠倒。
    //   现改为：canteen 只产出档口自身字段，均分由编排方（CanteenAdminController）
    //   调用 ReviewQueryService 补齐——controller 位于依赖图顶端，不产生新包级边。
    //   口径与出参（含无评价时按 0.00 兜底）保持不变。

    @Override
    public List<StallAdminVO> listAllForAdmin() {
        List<Stall> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .orderByAsc(Stall::getCanteenId)
                .orderByAsc(Stall::getSortOrder)
                .orderByDesc(Stall::getUpdatedAt));
        if (stalls.isEmpty()) {
            return List.of();
        }
        // BE-08 原为「一次 IN 查询取回全部档口平均分」以消除逐档口 N+1；该查询属 review 域，
        // 已上移至 CanteenAdminController#fillAvgRatings 统一编排（断开 canteen -> review 包级边）。
        // 本方法只负责档口自身字段，均分由调用方补齐；未补齐前保持 0.00 语义。
        return stalls.stream()
                .map(s -> toAdminVO(s, BigDecimal.ZERO))
                .collect(java.util.stream.Collectors.toList());
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

    /**
     * 定点写回档口楼层（纠错采纳专用，见接口契约）。
     * <p>
     * 用<b>部分实体</b>（只带 id + floor）走 {@code updateById}：MyBatis-Plus 的 NOT_NULL 更新策略
     * 保证 name / canteen_id / location / window_no / sort_order 等既有列原样保留，
     * 不会把楼层纠错放大成一次整行覆盖。
     * <p>
     * {@code updateById} 影响 0 行 ⇒ 目标档口已不存在（档口无删除能力，属并发/脏 id 兜底），
     * 抛 400 由调用方事务一并回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFloor(Long stallId, String floor) {
        String normalized = floor == null ? null : floor.trim();
        if (stallId == null || !StringUtils.hasText(normalized)) {
            throw new BusinessException("楼层不能为空");
        }
        Stall update = new Stall();
        update.setId(stallId);
        update.setFloor(normalized);
        if (stallMapper.updateById(update) == 0) {
            throw new BusinessException("档口不存在");
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

    @Override
    public Long findIdByName(String stallName) {
        if (!StringUtils.hasText(stallName)) {
            return null;
        }
        // 与 upsertStallByName 同一「精确匹配、LIMIT 1、无唯一键」口径（原为 correction 侧自查语句，逐字保留）
        Stall matched = stallMapper.selectOne(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getName, stallName)
                .last("LIMIT 1"));
        return matched == null ? null : matched.getId();
    }

    @Override
    public String getNameById(Long stallId) {
        if (stallId == null) {
            return null;
        }
        Stall stall = stallMapper.selectById(stallId);
        return stall == null ? null : stall.getName();
    }

    @Override
    public List<StallBriefVO> listBriefCandidates(String canteenName) {
        // 原实现在 correction 侧直接注入 CanteenMapper/StallMapper；「按名找食堂」属 canteen 域知识，现收回本域
        Canteen canteen = StringUtils.hasText(canteenName)
                ? canteenMapper.selectOne(new LambdaQueryWrapper<Canteen>()
                        .eq(Canteen::getName, canteenName)
                        .last("LIMIT 1"))
                : null;
        List<Stall> stalls = canteen != null
                ? stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .eq(Stall::getCanteenId, canteen.getId())
                        .orderByAsc(Stall::getSortOrder))
                : stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .orderByAsc(Stall::getCanteenId)
                        .orderByAsc(Stall::getSortOrder)
                        .orderByDesc(Stall::getUpdatedAt));
        return stalls.stream()
                .map(s -> new StallBriefVO(s.getId(), s.getName()))
                .toList();
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
