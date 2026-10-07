package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.canteen.constant.FloorDict;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.dto.StallBriefVO;
import com.bjtufood.canteen.dto.StallSaveReq;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.canteen.support.ImageColumnWriter;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
     * 「空值语义」的食堂/档口名称集合（upsert 时这类名称视为未填，不建档）。
     * 命中即回退 stallId 逻辑，绝不以其为名新建食堂/档口。
     */
    private static final Set<String> EMPTY_NAME_VALUES = Set.of("其他", "其它", "无", "未知");

    /** 新建档口时未提供有效所属食堂的报错文案（与 web 端「食堂必填」契约一致） */
    private static final String MSG_CANTEEN_REQUIRED = "请选择所属食堂";

    /** `stall.images VARCHAR(1024)` 的字符宽度上限（超长即 400，禁止静默截断） */
    private static final int IMAGES_JSON_MAX = 1024;

    /** 「同食堂下档口重名」的统一报错文案：新增与改名共用同一句，避免同语义两套措辞 */
    private static final String MSG_STALL_NAME_DUPLICATE = "该食堂下已存在同名档口";

    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final ImageUrlUtil imageUrlUtil;
    // 依赖方向：canteen **不注入** ReviewQueryService ——
    //   档口均分是 review 域按 dish 聚合出的**派生展示值**，不属于 canteen 的自有知识；
    //   若由 canteen 主动拉取，会形成 canteen -> review -> dish -> canteen 包级循环依赖。
    //   故 canteen 只产出档口自身字段，均分由编排方（CanteenAdminController）
    //   调用 ReviewQueryService 补齐——controller 位于依赖图顶端，不产生新包级边。
    //   口径与出参（含无评价时按 0.00 兜底）保持不变。

    @Override
    public List<StallAdminVO> listAllForAdmin() {
        return listAllForAdmin(null);
    }

    @Override
    public List<StallAdminVO> listAllForAdmin(Long canteenId) {
        // 排序口径（A2）：食堂升序 → 档口名升序 → 更新时间降序
        List<Stall> stalls = stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                .eq(canteenId != null, Stall::getCanteenId, canteenId)
                .orderByAsc(Stall::getCanteenId)
                .orderByAsc(Stall::getName)
                .orderByDesc(Stall::getUpdatedAt));
        if (stalls.isEmpty()) {
            return List.of();
        }
        // 食堂名一次批量取回：逐档口回查会让列表退化成 N 次查询（档口虽为十数条量级，
        // 查询数仍随行数线性放大）。跨行批量化范式与 CanteenAdminController#fillAvgRatings 一致。
        Map<Long, String> canteenNames = loadCanteenNames(stalls);
        // 均分由调用方批量补齐（编排在 CanteenAdminController#fillAvgRatings，以断开 canteen -> review 包级边）；
        // 本方法只负责档口自身字段，未补齐前保持 0.00 语义。
        return stalls.stream()
                .map(s -> toAdminVO(s, canteenNames.get(s.getCanteenId()), BigDecimal.ZERO))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StallSaveReq req) {
        // canteen_id=0 收口：canteenId 是必填字段，必须为有效食堂
        // （dish 列表/详情 joinDishSql 对 canteen 为 INNER JOIN，挂 0 的档口菜品会被静默剔除）。
        if (req.getCanteenId() == null || req.getCanteenId() <= 0
                || canteenMapper.selectById(req.getCanteenId()) == null) {
            throw new BusinessException(MSG_CANTEEN_REQUIRED);
        }
        // 楼层受控字典（值即汉字，唯一真源 FloorDict / docs/schema/stall.md）：
        // 缺省 = 保持原值（局部实体不带该列，updateById 的 NOT_NULL 策略跳过）；
        // 给了值就必须命中字典，否则会把字典外值写进档口楼层，端上与详情页随后无法解释该值；
        // 字典内没有「空楼层」⇒ 空白串一律 400，**不支持清空**（详见 docs/api/web/stalls.md 的空值语义表）。
        String floor = null;
        if (req.getFloor() != null) {
            if (!StringUtils.hasText(req.getFloor())) {
                throw new BusinessException("楼层不能为空");
            }
            if (!FloorDict.isValid(req.getFloor())) {
                throw new BusinessException("楼层不在预设范围内");
            }
            floor = FloorDict.normalize(req.getFloor());
        }
        // A2：改名同样受「**同食堂下**唯一」约束 —— 只在新增时校验的话，
        // 「把档口改名成同食堂已有的名」会绕过约束。归属由必填的 canteenId 给出，无需回查当前归属。
        String trimmed = req.getName() == null ? null : req.getName().trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessException("档口名称不能为空");
        }
        if (trimmed.length() > 64) {
            throw new BusinessException("档口名称不能超过 64 字");
        }
        DuplicateGuard.assertUnique(stallMapper, new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, req.getCanteenId())
                .eq(Stall::getName, trimmed)
                .ne(Stall::getId, id), MSG_STALL_NAME_DUPLICATE);
        // 局部实体 + updateById（NOT_NULL 策略）：只写本次提交的可编辑列，
        // 未提交的可选列（含时间列）不带值即不写列 —— 时间列由库的 ON UPDATE CURRENT_TIMESTAMP 维护
        // （实体时间字段无 fill 注解，见 docs/schema/README.md）。
        Stall patch = new Stall();
        patch.setId(id);
        patch.setCanteenId(req.getCanteenId());
        patch.setName(trimmed);
        if (floor != null) {
            patch.setFloor(floor);
        }
        // windowNo：缺省 = 保持原值（NOT_NULL 策略跳过）；给了值即覆盖，
        // 纯空白＝清空 —— 清空以空串落地（updateById 对 null 是「不写列」，无法表达清空），
        // 出参侧统一归一为空串，与「无窗口号」同形，端上无需判空。
        if (req.getWindowNo() != null) {
            patch.setWindowNo(StringUtils.hasText(req.getWindowNo()) ? req.getWindowNo() : "");
        }
        applyOptionalFields(patch, req);
        if (id == null || stallMapper.updateById(patch) == 0) {
            throw new BusinessException(4001, "档口不存在");
        }
    }

    /**
     * 写入 4 个可选列（位置 / 描述 / 图片 / 排序位）。
     * <p>
     * <b>null = 保持原值</b>（局部实体不带该列 ⇒ updateById 的 NOT_NULL 策略跳过；
     * 新建时可空列即落 NULL），给值即覆盖（空串 / 空数组表达「清空」）——
     * 与 {@code floor} / {@code windowNo} 的空值语义同构。
     */
    private void applyOptionalFields(Stall entity, StallSaveReq req) {
        if (req.getLocation() != null) {
            entity.setLocation(req.getLocation().trim());
        }
        if (req.getDescription() != null) {
            entity.setDescription(req.getDescription().trim());
        }
        if (req.getImages() != null) {
            entity.setImages(ImageColumnWriter.encode(imageUrlUtil, req.getImages(), IMAGES_JSON_MAX,
                    "档口图片地址过长（序列化后不得超过 " + IMAGES_JSON_MAX + " 字符）"));
        }
        if (req.getSortOrder() != null) {
            entity.setSortOrder(req.getSortOrder());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StallAdminVO createStall(StallSaveReq req) {
        // 校验与出参复用同一次食堂查询：食堂名由这里直接传给 toAdminVO，
        // 避免「校验查一次、填 canteenName 再查一次」的重复回查。
        Canteen canteen = req.getCanteenId() == null || req.getCanteenId() <= 0
                ? null
                : canteenMapper.selectById(req.getCanteenId());
        if (canteen == null) {
            throw new BusinessException(MSG_CANTEEN_REQUIRED);
        }
        String name = req.getName() == null ? null : req.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new BusinessException("档口名称不能为空");
        }
        if (name.length() > 64) {
            throw new BusinessException("档口名称不能超过 64 字");
        }
        // 同食堂下唯一（应用层校验，不加强 DB 唯一索引 —— 历史数据可能已有重复）
        DuplicateGuard.assertUnique(stallMapper, new LambdaQueryWrapper<Stall>()
                .eq(Stall::getCanteenId, req.getCanteenId())
                .eq(Stall::getName, name), MSG_STALL_NAME_DUPLICATE);
        // 楼层：可选；给了就必须命中受控字典（值即汉字，见 FloorDict）
        if (req.getFloor() != null) {
            if (!StringUtils.hasText(req.getFloor())) {
                throw new BusinessException("楼层不能为空");
            }
            if (!FloorDict.isValid(req.getFloor())) {
                throw new BusinessException("楼层不在预设范围内");
            }
        }
        Stall saved = new Stall();
        saved.setCanteenId(req.getCanteenId());
        saved.setName(name);
        saved.setFloor(FloorDict.normalize(req.getFloor()));
        // windowNo 为空 / 纯空白 → 落库 NULL（不留空串；出参侧归一为空串，端上无需判空）
        saved.setWindowNo(StringUtils.hasText(req.getWindowNo()) ? req.getWindowNo() : null);
        applyOptionalFields(saved, req);
        stallMapper.insert(saved);
        return toAdminVO(stallMapper.selectById(saved.getId()), canteen.getName(), BigDecimal.ZERO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStall(Long id) {
        if (id == null || stallMapper.selectById(id) == null) {
            throw new BusinessException(4001, "档口不存在");
        }
        stallMapper.deleteById(id);
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
        // 楼层受控字典（值即汉字）：纠错采纳同样必须命中字典，否则会把字典外值写进 stall.floor
        // （口径见 docs/func/web/B-UGC治理/B4-菜品问题反馈管理.md 与 docs/schema/stall.md）
        if (!FloorDict.isValid(normalized)) {
            throw new BusinessException("楼层不在预设范围内");
        }
        Stall update = new Stall();
        update.setId(stallId);
        update.setFloor(normalized);
        if (stallMapper.updateById(update) == 0) {
            throw new BusinessException(4001, "档口不存在");
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
        // 与 upsertStallByName 同一「精确匹配、LIMIT 1、无唯一键」口径
        Stall matched = stallMapper.selectOne(new LambdaQueryWrapper<Stall>()
                .eq(Stall::getName, stallName)
                .last("LIMIT 1"));
        return matched == null ? null : matched.getId();
    }

    @Override
    public String getFloorById(Long stallId) {
        if (stallId == null) {
            return null;
        }
        Stall stall = stallMapper.selectById(stallId);
        return stall == null ? null : stall.getFloor();
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
    public long countWithoutDish() {
        // `NOT IN (SELECT ...)` 用 EXISTS 语义更稳，但 MyBatis-Plus 的 notInSql 已足够且更易读；
        // 子查询只扫 dish.stall_id（档口量级为十数条），无性能顾虑。
        return stallMapper.selectCount(new LambdaQueryWrapper<Stall>()
                .notInSql(Stall::getId, "SELECT stall_id FROM dish WHERE stall_id > 0"));
    }

    @Override
    public String getCanteenNameByStallId(Long stallId) {
        if (stallId == null) {
            return null;
        }
        Stall stall = stallMapper.selectById(stallId);
        if (stall == null || stall.getCanteenId() == null) {
            return null;
        }
        Canteen canteen = canteenMapper.selectById(stall.getCanteenId());
        return canteen == null ? null : canteen.getName();
    }

    @Override
    public List<StallBriefVO> listBriefCandidates(String canteenName) {
        // 「按名找食堂」属 canteen 域知识：correction 侧只消费本方法，不自行注入 CanteenMapper/StallMapper
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
     * （收口：旧逻辑返回 0L 会产生 canteen_id=0 的档口，其菜品被
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

    /**
     * 批量取「食堂 ID → 食堂名」（列表填充 {@code canteenName} 用）。
     * <p>
     * 一次 {@code IN} 查询取回列表涉及的全部食堂，替代逐档口 {@code selectById} 的 N+1。
     * 未挂食堂（{@code canteenId} 为空）或食堂已不存在时不出现在映射中，调用方按 null 落值 ——
     * 与逐档口回查同口径（查不到即为 null）。
     */
    private Map<Long, String> loadCanteenNames(List<Stall> stalls) {
        Set<Long> canteenIds = stalls.stream()
                .map(Stall::getCanteenId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (canteenIds.isEmpty()) {
            return Map.of();
        }
        List<Canteen> canteens = canteenMapper.selectBatchIds(canteenIds);
        if (canteens == null || canteens.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>(canteens.size());
        for (Canteen canteen : canteens) {
            if (canteen != null && canteen.getId() != null) {
                names.put(canteen.getId(), canteen.getName());
            }
        }
        return names;
    }

    /**
     * 档口实体 → 后台 VO。
     *
     * @param canteenName 所属食堂名，由调用方一次/批量取回后传入
     *                    （列表场景见 {@link #loadCanteenNames}）——本方法不做任何回查
     */
    private StallAdminVO toAdminVO(Stall stall, String canteenName, BigDecimal avgRating) {
        StallAdminVO vo = new StallAdminVO();
        vo.setId(stall.getId());
        vo.setCanteenId(stall.getCanteenId());
        vo.setCanteenName(canteenName);
        vo.setName(stall.getName());
        // 出参空值口径：可空字符串列（含保留列）恒非空串，端上无需判空
        // （与 client 侧菜品详情的 COALESCE(s.floor, '') 同口径）
        vo.setLocation(orEmpty(stall.getLocation()));
        // 楼层/窗口号（端上有消费：档口卡展示位置）。
        vo.setFloor(orEmpty(stall.getFloor()));
        vo.setWindowNo(orEmpty(stall.getWindowNo()));
        vo.setDescription(orEmpty(stall.getDescription()));
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(stall.getImages()));
        // 档口评分统一实时聚合（均分不落库，与 toVO 同口径，避免两端不一致）
        // BE-08：avgRating 由批量 IN 查询一次性取回；无评价（不在结果集）按 0.00 兜底
        vo.setAvgRating((avgRating != null ? avgRating : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        vo.setSortOrder(stall.getSortOrder());
        vo.setUpdatedAt(stall.getUpdatedAt());
        return vo;
    }

    /** 出参空值归一：{@code null} → 空串（后台列表的字符串列恒非空串，端上无需判空） */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
