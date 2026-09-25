package com.bjtufood.correction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.AuthStateUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.correction.constant.CorrectionConst;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import com.bjtufood.correction.service.CorrectionService;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.notify.constant.NotificationConst;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品信息纠错服务实现（独立资源：POST /dishes/{id}/correction + /admin/corrections）。
 * <p>
 * 提交：七字段快照落库（食堂名/档口名为自由文本，无字典端点），status=pending；
 * 采纳：两段式档口确认后七字段写回 dish；拒绝：reply + rejectReason 留痕；
 * 两种处理结论均向可归属提交人投递「菜品信息更新」站内回执（归属判据/投递口径同 feedback handle）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionServiceImpl implements CorrectionService {

    private final DishCorrectionMapper correctionMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    /** 按名 upsert 档口 / 档口存在性校验（与菜品录入编辑共用同一入口，勿在此复制实现） */
    private final StallService stallService;
    private final UserMapper userMapper;
    private final SensitiveFilter sensitiveFilter;
    private final NotificationService notificationService;
    private final ImageUrlUtil imageUrlUtil;

    // ==================== 提交（POST /dishes/{id}/correction） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, Long dishId, DishCorrectionReq req) {
        Dish dish = dishMapper.selectById(dishId);
        // 菜品不存在与已下架同款处理（对公开接口而言「下架」等价于「不存在」，与 DishServiceImpl 详情口径一致）
        if (dish == null || !DishConst.STATUS_ON.equals(dish.getStatus())) {
            throw new BusinessException(4001, "菜品不存在");
        }
        // name：trim 后必填（DTO @NotBlank 为 Controller 层主拦截，此处兜底同口径 400）
        String name = req.getName() == null ? null : req.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "菜品名称不能为空");
        }
        if (name.length() > CorrectionConst.NAME_MAX_LENGTH) {
            throw new BusinessException(400, "菜品名称不能超过" + CorrectionConst.NAME_MAX_LENGTH + "字");
        }
        // 敏感词命中即 400（写回字段不放行替换版，保持用户提交原词落库）
        if (sensitiveFilter.containsSensitive(name)) {
            throw new BusinessException(400, "菜品名称包含违规内容，请修改后重新提交");
        }
        // canteenName / stallName：自由文本，trim 后必填（≤64 字由 DTO @Size 拦截）
        String canteenName = req.getCanteenName() == null ? null : req.getCanteenName().trim();
        if (!StringUtils.hasText(canteenName)) {
            throw new BusinessException(400, "食堂名称不能为空");
        }
        String stallName = req.getStallName() == null ? null : req.getStallName().trim();
        if (!StringUtils.hasText(stallName)) {
            throw new BusinessException(400, "档口名称不能为空");
        }
        // price：必填整数 >0（分，DTO @NotNull/@Positive 为 Controller 层主拦截，此处兜底）
        if (req.getPrice() == null || req.getPrice() <= 0) {
            throw new BusinessException(400, "价格必须为大于 0 的整数（单位：分）");
        }

        DishCorrection correction = new DishCorrection();
        correction.setDishId(dishId);
        correction.setUserId(userId);
        correction.setName(name);
        correction.setPrice(req.getPrice());
        correction.setCanteenName(canteenName);
        correction.setStallName(stallName);
        correction.setFlavorTags(normalizeStringList(req.getFlavorTags()));
        correction.setIngredients(normalizeStringList(req.getIngredients()));
        correction.setImages(encodeImages(req.getImages()));
        correction.setStatus(CorrectionConst.STATUS_PENDING);
        correctionMapper.insert(correction);
    }

    /** 字符串数组归一化：逐项 trim、去空白项；null 原样返回（不强制非空） */
    private List<String> normalizeStringList(List<String> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(String::trim).filter(StringUtils::hasText).toList();
    }

    /**
     * 纠错配图校验与序列化：≤{@link CorrectionConst#IMAGE_MAX} 张且逐项 COS 白名单校验
     * （安检转存链路复用 feedback images 的实现方式——发生在上传时，此处只做受信任地址校验）。
     *
     * @return 序列化前的归一化列表；无有效配图返回 null（不落库空数组）
     */
    private List<String> encodeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> normalized = normalizeStringList(images);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > CorrectionConst.IMAGE_MAX) {
            throw new BusinessException(400, "菜品图片最多 " + CorrectionConst.IMAGE_MAX + " 张");
        }
        for (String url : normalized) {
            if (!imageUrlUtil.isValidCosUgcUrl(url)) {
                throw new BusinessException(400, "图片地址不合法，请重新上传");
            }
        }
        return normalized;
    }

    // ==================== 管理端列表（GET /admin/corrections） ====================

    @Override
    public IPage<DishCorrectionAdminVO> listForAdmin(String status, int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        // 查询入参白名单校验（PR-06）：非法值 400，不静默进 SQL 恒空（掩盖真实积压）
        status = ParamValidator.optionalInWhitelist(status, CorrectionConst.QUERY_STATUSES, "处理状态");

        LambdaQueryWrapper<DishCorrection> wrapper = new LambdaQueryWrapper<DishCorrection>()
                .eq(StringUtils.hasText(status), DishCorrection::getStatus, status)
                .orderByDesc(DishCorrection::getCreatedAt);
        IPage<DishCorrection> p = correctionMapper.selectPage(new Page<>(page, pageSize), wrapper);

        // 批量补齐提交人昵称（一次 IN 查询，消除 N+1；游客 userId=null 不参与）
        List<Long> userIds = p.getRecords().stream()
                .map(DishCorrection::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                    .forEach(u -> userMap.put(u.getId(), u.getNickname()));
        }

        // 批量补齐目标菜品名（一次 IN 查询）：只取 id/name 两列；不过滤上架态——纠错对象可能已被下架，
        // 管理端仍需看到菜品名回看内容；菜品已物理删除时不在结果集，VO 保持 null。
        Map<Long, String> dishNameMap = batchDishNames(p.getRecords());

        IPage<DishCorrectionAdminVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream()
                .map(c -> toAdminVO(c, userMap, dishNameMap))
                .toList());
        return result;
    }

    /** 批量查询本页纠错目标菜品名：dishId 去重后一次 IN 查询，空集合返回空 Map（不发起查询） */
    private Map<Long, String> batchDishNames(List<DishCorrection> records) {
        List<Long> dishIds = records.stream()
                .map(DishCorrection::getDishId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>(dishIds.size());
        dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .select(Dish::getId, Dish::getName)
                        .in(Dish::getId, dishIds))
                .forEach(d -> map.put(d.getId(), d.getName()));
        return map;
    }

    /** 管理端 VO 转换：补齐菜品名、提交人昵称、配图（相对路径 → 绝对 URL） */
    private DishCorrectionAdminVO toAdminVO(DishCorrection c, Map<Long, String> userMap, Map<Long, String> dishNameMap) {
        DishCorrectionAdminVO vo = new DishCorrectionAdminVO();
        vo.setId(c.getId());
        vo.setDishId(c.getDishId());
        vo.setDishName(c.getDishId() != null ? dishNameMap.get(c.getDishId()) : null);
        vo.setUserId(c.getUserId());
        vo.setUserNickname(userMap.get(c.getUserId()));
        vo.setName(c.getName());
        vo.setPrice(c.getPrice());
        vo.setCanteenName(c.getCanteenName());
        vo.setStallName(c.getStallName());
        vo.setFlavorTags(c.getFlavorTags() == null ? List.of() : c.getFlavorTags());
        vo.setIngredients(c.getIngredients() == null ? List.of() : c.getIngredients());
        List<String> images = c.getImages() == null ? List.of() : c.getImages();
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        vo.setStatus(c.getStatus());
        vo.setReply(c.getReply());
        vo.setRejectReason(c.getRejectReason());
        vo.setHandledAt(c.getHandledAt());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    // ==================== 管理端采纳（POST /admin/corrections/{id}/adopt，两段式档口确认） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StallConfirmVO adopt(Long id, DishCorrectionAdoptReq req) {
        DishCorrection correction = correctionMapper.selectById(id);
        if (correction == null) {
            throw new BusinessException("纠错不存在");
        }
        // 前置：status=pending，否则 400（幂等：重复调用 = 已处理 →「该纠错已处理」）
        if (!CorrectionConst.STATUS_PENDING.equals(correction.getStatus())) {
            throw new BusinessException(400, "该纠错已处理");
        }
        // 目标菜品物理删除 → 4001（采纳无从写回）
        Dish dish = dishMapper.selectById(correction.getDishId());
        if (dish == null) {
            throw new BusinessException(4001, "菜品不存在，无法采纳");
        }

        // ---- 两段式档口确认：解析最终挂靠档口 ----
        Long resolvedStallId;
        if (req != null && req.getStallId() != null) {
            // 第二段（管理端选定既有档口）：显式 stallId 优先，校验存在后挂靠
            if (!stallService.existsById(req.getStallId())) {
                throw new BusinessException(400, "档口不存在");
            }
            resolvedStallId = req.getStallId();
        } else {
            Stall matched = stallMapper.selectOne(new LambdaQueryWrapper<Stall>()
                    .eq(Stall::getName, correction.getStallName())
                    .last("LIMIT 1"));
            if (matched != null) {
                // 提交档口名与现有档口归一化精确匹配命中 → 直接采纳
                resolvedStallId = matched.getId();
            } else if (req != null && Boolean.TRUE.equals(req.getCreateIfMissing())) {
                // 第二段（确认新建）：按提交档口名 upsert（所属食堂按提交食堂名 upsert，
                // 空值语义名称 → 400「请选择所属食堂」，与菜品录入编辑同口径）
                resolvedStallId = stallService.upsertStallByName(correction.getStallName(), correction.getCanteenName());
            } else {
                // 未命中且未指定档口/未确认新建 → 不执行采纳，返回候选档口供管理端选择
                return new StallConfirmVO(true, buildCandidates(correction.getCanteenName()));
            }
        }

        applyAdoption(dish, correction, resolvedStallId);
        // 站内回执（提交人非空且已认证时投递，失败不阻塞采纳）
        sendCorrectionReceipt(correction, true, CorrectionConst.ADOPT_REPLY, null);
        return null;
    }

    /**
     * 采纳写回：七字段写回 dish（name/price/档口挂靠/flavorTags/ingredients/images）——
     * 可空快照字段（flavorTags/ingredients/images）不覆盖既有值（MyBatis-Plus NOT_NULL 策略跳过 null），
     * 保护「菜品首图必填」等既有不变量；随后纠错记录归档（status/reply/handled_at，
     * stall_name 以实际挂靠档口名落库，管理端指定档口可能不同于提交名）。
     */
    private void applyAdoption(Dish dish, DishCorrection correction, Long resolvedStallId) {
        Stall stall = stallMapper.selectById(resolvedStallId);
        Dish update = new Dish();
        update.setId(dish.getId());
        update.setName(correction.getName());
        update.setPrice(correction.getPrice());
        update.setStallId(resolvedStallId);
        if (correction.getFlavorTags() != null) {
            update.setFlavorTags(correction.getFlavorTags());
        }
        if (correction.getIngredients() != null) {
            update.setIngredients(correction.getIngredients());
        }
        if (correction.getImages() != null && !correction.getImages().isEmpty()) {
            // Dish.images 为 JSON 串存储（与 Dish 实体既有口径一致）
            update.setImages(JsonListUtil.toJson(correction.getImages()));
        }
        if (dishMapper.updateById(update) == 0) {
            // 并发删除兜底（采纳前置已查到菜品）
            throw new BusinessException(4001, "菜品不存在，无法采纳");
        }

        correction.setStatus(CorrectionConst.STATUS_ADOPTED);
        correction.setReply(CorrectionConst.ADOPT_REPLY);
        correction.setRejectReason(null);
        correction.setHandledAt(LocalDateTime.now());
        if (stall != null && !correction.getStallName().equals(stall.getName())) {
            correction.setStallName(stall.getName());
        }
        correctionMapper.updateById(correction);
    }

    /**
     * 构建档口确认候选列表：提交食堂名匹配现有食堂时 = 该食堂下全部档口（sort_order 升序）；
     * 无匹配食堂时 = 全量档口（canteen_id 升序 → sort_order 升序，与既有档口排序口径一致）。
     */
    private List<StallConfirmVO.StallCandidate> buildCandidates(String canteenName) {
        Canteen canteen = canteenMapper.selectOne(new LambdaQueryWrapper<Canteen>()
                .eq(Canteen::getName, canteenName)
                .last("LIMIT 1"));
        List<Stall> stalls = canteen != null
                ? stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .eq(Stall::getCanteenId, canteen.getId())
                        .orderByAsc(Stall::getSortOrder))
                : stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .orderByAsc(Stall::getCanteenId)
                        .orderByAsc(Stall::getSortOrder)
                        .orderByDesc(Stall::getUpdatedAt));
        return stalls.stream()
                .map(s -> new StallConfirmVO.StallCandidate(s.getId(), s.getName()))
                .toList();
    }

    // ==================== 管理端拒绝（PUT /admin/corrections/{id}） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, DishCorrectionHandleReq req) {
        DishCorrection correction = correctionMapper.selectById(id);
        if (correction == null) {
            throw new BusinessException("纠错不存在");
        }
        // 前置：status=pending，否则 400（幂等：重复调用 = 已处理 →「该纠错已处理」）
        if (!CorrectionConst.STATUS_PENDING.equals(correction.getStatus())) {
            throw new BusinessException(400, "该纠错已处理");
        }
        // 处理结论：本端点即拒绝动作，outcome 固定 rejected（形态对齐 feedback handle，非法值 400）
        String outcome = req.getOutcome() == null || req.getOutcome().isBlank()
                ? CorrectionConst.OUTCOME_REJECTED
                : req.getOutcome().trim();
        if (!CorrectionConst.OUTCOME_REJECTED.equals(outcome)) {
            throw new BusinessException(400, "处理结论非法（本端点仅支持 rejected=不采纳/退回）");
        }
        // reply 必填（§7.16 同源口径）：DTO @NotBlank 为主拦截，Service 层兜底（同口径、同错误码 400）
        String trimmedReply = req.getReply() == null ? null : req.getReply().trim();
        if (!StringUtils.hasText(trimmedReply)) {
            throw new BusinessException(400, "请填写处理回复（提交人将收到该内容）");
        }
        // 不采纳原因必填（§7.23 第 5 条同源口径）：1~200 字，纯空白视为未填写 → 400
        String rejectReason = req.getRejectReason() == null ? null : req.getRejectReason().trim();
        if (!StringUtils.hasText(rejectReason)) {
            throw new BusinessException(400, "请填写不采纳原因");
        }
        if (rejectReason.length() > CorrectionConst.REJECT_REASON_MAX_LENGTH) {
            throw new BusinessException(400, "不采纳原因不能超过" + CorrectionConst.REJECT_REASON_MAX_LENGTH + "字");
        }

        correction.setStatus(CorrectionConst.STATUS_REJECTED);
        correction.setReply(trimmedReply);
        correction.setRejectReason(rejectReason);
        correction.setHandledAt(LocalDateTime.now());
        correctionMapper.updateById(correction);
        // 站内回执（携带不采纳原因与处理说明）
        sendCorrectionReceipt(correction, false, trimmedReply, rejectReason);
    }

    // ==================== 站内回执 ====================

    /**
     * 纠错处理回执（采纳/拒绝统一入口，参考 feedback handle 通知实现）。
     * <p>
     * 归属判据：提交时带 userId（登录态）且该账号已邮箱认证（bind_email 非空，唯一真源 AuthStateUtil）；
     * 游客（userId 为空）与未认证账号不投递——纠错主路径刻意匿名，不保留可回执身份。
     * 投递失败不影响处理结果（独立 try 分支，异常不外抛到主流程）。
     *
     * @param adopted      true=采纳（reply 为固定采纳文案）；false=拒绝（rejectReason 非空）
     * @param reply        处理说明（采纳=固定文案；拒绝=管理员回复）
     * @param rejectReason 不采纳原因（adopted=false 时非空；否则为 null，不参与文案）
     */
    private void sendCorrectionReceipt(DishCorrection correction, boolean adopted, String reply, String rejectReason) {
        Long userId = correction.getUserId();
        if (userId == null) {
            return;
        }
        try {
            User user = userMapper.selectById(userId);
            if (user == null || !AuthStateUtil.isVerified(user.getBindEmail())) {
                return;
            }
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(NotificationConst.TYPE_CORRECTION_HANDLE);
            n.setRelatedId(correction.getId());
            n.setIsRead(0);
            n.setTitle("菜品信息更新");
            n.setContent(adopted
                    ? "你提交的菜品信息纠错已采纳，菜品信息已更新。处理说明：" + reply
                    : "你提交的菜品信息纠错未采纳：" + rejectReason + "。处理说明：" + reply);
            notificationService.notify(n);
        } catch (Exception ignored) {
            // 回执失败不阻塞纠错处理
        }
    }
}
