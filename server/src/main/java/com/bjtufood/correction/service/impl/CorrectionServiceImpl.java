package com.bjtufood.correction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.correction.constant.CorrectionConst;
import com.bjtufood.correction.dto.DishCorrectionAdoptReq;
import com.bjtufood.correction.dto.DishCorrectionAdminVO;
import com.bjtufood.correction.dto.DishCorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionDifferenceVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.dto.StallConfirmVO;
import com.bjtufood.correction.entity.DishCorrection;
import com.bjtufood.correction.mapper.DishCorrectionMapper;
import com.bjtufood.correction.service.CorrectionService;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishCorrectionCmd;
import com.bjtufood.dish.dto.DishDimensionAdminVO;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.notification.dto.NotificationCmd;
import com.bjtufood.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜品问题反馈服务实现（独立资源：POST /dishes/{id}/correction + /admin/corrections）。
 * <p>
 * **提交按 {@code type} 分派**：`field`（信息有误）走局部提交（patch）——只落库改动项
 * （食堂名 / 档口名 / 楼层为自由文本，无字典端点），status=pending；`gone`（已经下架）走一键提交。
 * **管理端处置同样按 `type` 分派**：
 * <ul>
 *   <li>`field`：两段式档口确认后按选中差异项写回 dish（{@code floor} 例外，写回<b>目标档口</b>
 *       {@code stall.floor}）；</li>
 *   <li>`gone`：<b>仅下架</b>（{@code dish.status=off}，可逆、评价保留，绝不物理删除）；</li>
 *   <li>驳回：`reply` + `rejectReason` 留痕。</li>
 * </ul>
 * 两种处理结论均向可归属提交人投递站内回执，**文案按 `type` 与「是否部分采纳」分派**
 * （归属判据 / 投递口径同 feedback handle）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionServiceImpl implements CorrectionService {

    private final DishCorrectionMapper correctionMapper;
    /**
     * 落库事务边界：
     * {@code submit} 的 {@code @Transactional} 原先从方法入口就开始、横跨微信机审的 HTTP 外呼（超时 5s）
     * ⇒ 期间一直占用数据库连接；HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
     * 现改为「先机审（无事务）→ 再落库（开事务）」。
     * 必须是**独立 Bean**：Spring 事务靠代理生效，同类自调用不会开启事务。
     */
    private final CorrectionPersister correctionPersister;
    /** 跨域契约：菜品存在性/在售判定、菜品名投影、采纳写回（P0-1，替代 DishMapper 直连） */
    private final DishService dishService;
    /** 跨域只读契约（dish.service 接口）：维度字典，供差异对照下发**维度中文名**（端上零硬编码） */
    private final DishAttributeAdminService attributeAdminService;
    /** 按名 upsert 档口 / 档口存在性校验 / 档口名解析 / 候选档口列表（与菜品录入编辑共用同一入口，勿在此复制实现） */
    private final StallService stallService;
    /** 跨域只读契约：管理端「提交人」昵称投影 + 内容安检取 openid（P0-1，替代 UserMapper 直连） */
    private final UserService userService;
    private final LocalSensitiveFilter localSensitiveFilter;
    /** 微信内容安全检测 */
    private final ContentSecurityService contentSecurityService;
    private final NotificationService notificationService;
    private final ImageUrlUtil imageUrlUtil;

    // ==================== 提交（POST /dishes/{id}/correction） ====================

    /**
     * 提交纠错。<b>本方法刻意不加 {@code @Transactional}</b>：事务边界收窄到落库一步
     * （{@link CorrectionPersister#insert}），使微信机审的外呼期间不占用数据库连接。
     * <p>
     * 注：{@code adopt} / {@code reject} 不含外部 HTTP 调用，事务边界保持原样。
     */
    @Override
    public void submit(Long userId, Long dishId, DishCorrectionReq req) {
        // 菜品不存在与已下架同款处理（对公开接口而言「下架」等价于「不存在」，与 DishServiceImpl 详情口径一致）；
        // 存在性 + 在售态口径由 dish 域唯一持有（P0-1：correction 不再 import Dish 实体/DishConst/DishMapper）
        if (!dishService.existsOnSale(dishId)) {
            throw new BusinessException(4001, "菜品不存在");
        }
        // 匿名提交归一为 userId = 0（与 user_feedback 同口径：库列 NOT NULL DEFAULT 0，0 = 无归属）
        long ownerId = userId == null ? ANONYMOUS_USER_ID : userId;
        // type 分派：gone（已经下架）走「一键提交」路径，其余走 field 的局部提交路径。
        // 两条路径的字段集合、校验规则、落库内容完全不同，故在此处分流而非混在一段校验里。
        if (CorrectionConst.TYPE_GONE.equals(req.getType())) {
            submitGone(ownerId, dishId, req);
            return;
        }
        submitField(ownerId, dishId, req);
    }

    /**
     * <b>type=gone（已经下架）· 一键提交</b>。
     * <p>
     * 核心约束：<b>note / images 均为选填，允许全不传</b>——提交即成立，这是「用户是事实校对员」
     * 这一产品定位的落地边界；一旦要求必填，用户成本从「点一下」回升到「填表」，提交量将大幅下降。
     * <p>
     * 同时<b>拒绝任何差异项字段</b>：那些字段表达的是「这道菜存在，只是信息写错了」，
     * 与「这道菜消失了」语义冲突；用户想补录信息应改选 {@code field} 型（或由管理员在 A3 直接编辑）。
     * <p>
     * <b>去重</b>：同一用户对同一菜品的 gone 型只计一次（重复提交返回成功但不重复计数）——
     * 这是<b>防单人刷队列</b>，<b>不是决策门槛</b>（≥1 条即进待办，是否下架由管理员人工决定）。
     */
    private void submitGone(Long userId, Long dishId, DishCorrectionReq req) {
        if (req.getName() != null || req.getPrice() != null || req.getCanteenName() != null
                || req.getStallName() != null || req.getFloor() != null
                || (req.getAttributes() != null && !req.getAttributes().isEmpty())) {
            throw new BusinessException(400, "「已经下架」不能提交菜品信息，若信息有误请选择对应类型");
        }
        String note = null;
        if (req.getNote() != null) {
            note = req.getNote().trim();
            if (!StringUtils.hasText(note)) {
                // 纯空白视为「未填」——折叠区里清空后提交不应报错
                note = null;
            } else if (note.length() > CorrectionConst.NOTE_MAX_LENGTH) {
                throw new BusinessException(400, "补充说明不能超过" + CorrectionConst.NOTE_MAX_LENGTH + "字");
            }
        }
        List<String> images = encodeImages(req.getImages(), CorrectionConst.GONE_IMAGE_MAX);

        // 机审：仅 note 含自由文本；为空则跳过（不产生多余的微信调用）
        checkUgcText(userId, note == null ? "" : note);

        // 去重：同一用户 + 同一菜品已有一条未处理的 gone 反馈 ⇒ 直接返回成功，不重复计数
        if (existsPendingGone(userId, dishId)) {
            log.info("[菜品问题反馈] gone 型重复提交已忽略：userId={}, dishId={}", userId, dishId);
            return;
        }

        DishCorrection correction = new DishCorrection();
        correction.setType(CorrectionConst.TYPE_GONE);
        correction.setDishId(dishId);
        correction.setUserId(userId);
        // gone 行各差异项列恒 NULL —— 语义是「无可对照的原值」，不是「未改动」
        correction.setNote(note);
        correction.setImages(images);
        correction.setStatus(CorrectionConst.STATUS_PENDING);
        correctionPersister.insert(correction);
    }

    /**
     * 是否已存在同一用户对同一菜品的「待处理 gone 反馈」。
     * <p>
     * 匿名提交（{@code userId == 0}）<b>不去重</b>：无法归属到人，若也去重则所有匿名提交
     * 会互相挤掉（第一条之后全部被忽略），等于变相禁用匿名反馈。
     *
     * @param userId 提交人（{@code 0} = 匿名）
     * @param dishId 菜品
     * @return true = 已有待处理记录，本次不再重复落库
     */
    private boolean existsPendingGone(Long userId, Long dishId) {
        if (userId == null || userId == ANONYMOUS_USER_ID) {
            return false;
        }
        LambdaQueryWrapper<DishCorrection> wrapper = new LambdaQueryWrapper<DishCorrection>()
                .eq(DishCorrection::getType, CorrectionConst.TYPE_GONE)
                .eq(DishCorrection::getDishId, dishId)
                .eq(DishCorrection::getUserId, userId)
                .eq(DishCorrection::getStatus, CorrectionConst.STATUS_PENDING);
        return correctionMapper.exists(wrapper);
    }

    /**
     * <b>type=field（信息有误）· 局部提交</b>（原 submit 主体，行为保持不变）。
     */
    private void submitField(Long userId, Long dishId, DishCorrectionReq req) {
        // 局部提交（patch）：**仅传改动项**。未传 = 保持原值；传入即校验（不静默降级）。
        // 空请求体（无任何改动项）→ 400「未提交任何改动」——无可提交内容时禁止落库。
        String name = null;
        if (req.getName() != null) {
            name = req.getName().trim();
            if (!StringUtils.hasText(name)) {
                throw new BusinessException(400, "菜品名称不能为空");
            }
            if (name.length() > CorrectionConst.NAME_MAX_LENGTH) {
                throw new BusinessException(400, "菜品名称不能超过" + CorrectionConst.NAME_MAX_LENGTH + "字");
            }
            // 敏感词命中即 400（写回字段不放行替换版，保持用户提交原词落库）
            if (localSensitiveFilter.containsSensitive(name)) {
                throw new BusinessException(400, "菜品名称包含违规内容，请修改后重新提交");
            }
        }
        // canteenName / stallName：自由文本（无字典 / 无 picker），传入即校验非空与长度
        String canteenName = null;
        if (req.getCanteenName() != null) {
            canteenName = req.getCanteenName().trim();
            if (!StringUtils.hasText(canteenName)) {
                throw new BusinessException(400, "食堂名称不能为空");
            }
            if (canteenName.length() > CorrectionConst.CANTEEN_NAME_MAX_LENGTH) {
                throw new BusinessException(400, "食堂名称不能超过" + CorrectionConst.CANTEEN_NAME_MAX_LENGTH + "字");
            }
        }
        String stallName = null;
        if (req.getStallName() != null) {
            stallName = req.getStallName().trim();
            if (!StringUtils.hasText(stallName)) {
                throw new BusinessException(400, "档口名称不能为空");
            }
            if (stallName.length() > CorrectionConst.STALL_NAME_MAX_LENGTH) {
                throw new BusinessException(400, "档口名称不能超过" + CorrectionConst.STALL_NAME_MAX_LENGTH + "字");
            }
        }
        // floor：楼层（自由文本，归属档口），传入即校验非空与长度。
        // 长度上限与 stall.floor VARCHAR(16) 严格一致——采纳会把提交值原样写回该列，上限不一致会静默截断/报错。
        String floor = null;
        if (req.getFloor() != null) {
            floor = req.getFloor().trim();
            if (!StringUtils.hasText(floor)) {
                throw new BusinessException(400, "楼层不能为空");
            }
            if (floor.length() > CorrectionConst.FLOOR_MAX_LENGTH) {
                throw new BusinessException(400, "楼层超长");
            }
        }
        // price：传入即校验 >0 的整数（分）
        Integer price = req.getPrice();
        if (price != null && price <= 0) {
            throw new BusinessException(400, "价格必须为大于 0 的整数（单位：分）");
        }
        // attributes：仅含用户改动的维度；空对象视为未提供（无改动）
        Map<String, Object> attributes = req.getAttributes() == null || req.getAttributes().isEmpty()
                ? null : req.getAttributes();
        List<String> images = encodeImages(req.getImages(), CorrectionConst.IMAGE_MAX);

        // field 型不接受 note（note 是 gone 型的选填补充）——静默丢弃会让用户以为写进去了
        if (req.getNote() != null && StringUtils.hasText(req.getNote().trim())) {
            throw new BusinessException(400, "「信息有误」无需补充说明，请选择对应类型");
        }

        // floor 计入改动项：**仅改楼层**的一次提交（如 1F → 2F）也是有效纠错，不得判「未提交任何改动」
        if (name == null && price == null && canteenName == null && stallName == null && floor == null
                && attributes == null && images == null) {
            throw new BusinessException(400, "未提交任何改动");
        }

        // ---- 微信内容安全检测----
        // 纠错的 name / canteenName / stallName / floor / attributes 均为**用户自由文本**，
        // 且纠错内容会被管理员**采纳并写回**（进入公开展示：菜品经 dish、楼层经 stall.floor），
        // 仅靠本地静态词库不足以覆盖谐音/变体/语义违规。
        // 口径与 feedback / review / 昵称一致：risky 由 checkText 统一拦截为 400。
        // 合并为**单次**调用送检（见 mergeModerationText）：msgSecCheck 按调用计费且有 2500 字上限，
        // 分字段多次送检会成倍放大微信调用额度，故拼接后一次提交。
        checkUgcText(userId, mergeModerationText(name, canteenName, stallName, floor, attributes));

        DishCorrection correction = new DishCorrection();
        correction.setType(CorrectionConst.TYPE_FIELD);
        correction.setDishId(dishId);
        correction.setUserId(userId);
        correction.setName(name);
        correction.setPrice(price);
        correction.setCanteenName(canteenName);
        correction.setStallName(stallName);
        correction.setFloor(floor);
        correction.setAttributes(JsonMapUtil.toJson(attributes));
        correction.setImages(images);
        correction.setStatus(CorrectionConst.STATUS_PENDING);
        correctionPersister.insert(correction);
    }

    /**
     * 配图校验与序列化：≤{@code maxImages} 张且逐项 COS 白名单校验
     * （安检转存链路复用 feedback images 的实现方式——发生在上传时，此处只做受信任地址校验）。
     * <p>
     * 上限<b>按 type 分派</b>：field 型 {@link CorrectionConst#IMAGE_MAX}（改动项佐证，多张）；
     * gone 型 {@link CorrectionConst#GONE_IMAGE_MAX}（路过随手拍，少量即可）。
     *
     * @param images    原始图片列表（可为 null / 空）
     * @param maxImages 该type 允许的张数上限
     * @return 序列化前的归一化列表；无有效配图返回 null（不落库空数组）
     */
    private List<String> encodeImages(List<String> images, int maxImages) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> normalized = images.stream().map(String::trim).filter(StringUtils::hasText).toList();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > maxImages) {
            throw new BusinessException(400, "菜品图片最多 " + maxImages + " 张");
        }
        for (String url : normalized) {
            if (!imageUrlUtil.isValidCosUgcUrl(url)) {
                throw new BusinessException(400, "图片地址不合法，请重新上传");
            }
        }
        return normalized;
    }

    // ==================== 微信内容安全检测 ====================

    /**
     * 合并纠错中所有<b>用户自由文本</b>字段，拼接为<b>单条</b>待检文本。
     * <p>
     * <b>为何合并而不是逐字段送检</b>：{@code msgSecCheck} 按<b>调用次数</b>计费，
     * 一次纠错最多可提交 name / canteenName / stallName / floor / attributes 五类文本，
     * 逐字段送检会把微信调用额度放大到 5 倍；合并为一次则<b>恒定 1 次</b>。
     * <p>
     * <b>为何用换行分隔而不是直接拼接</b>：若直接相连，两个字段的边界词可能
     * 偶然拼出一个新词造成误判；换行是 msgSecCheck 认可的分隔符，且语义上
     * 与「多行提交一份表单」一致，命中位置的语义也更贴近用户实际填写的内容。
     * <p>
     * <b>attributes 为何要展开</b>：维度值可能是自由文本（候选为空的维度允许用户自填），
     * 用 {@code JsonMapUtil.toJson} 还原为可读 JSON 文本再送检——既覆盖了其中可能夹带的
     * 违规文本，又不引入结构化数据给微信解析器的兼容性问题。
     * <p>
     * 全部字段皆空（局部提交只改了 price / images）时返回空串，由
     * {@link #checkUgcText} 的空值判断直接跳过，不产生多余的微信调用。
     *
     * @param floor 楼层（自由文本，归属档口；采纳时写回 {@code stall.floor} 进入公开展示，
     *              故与其余自由文本字段同等待遇，一并送检）
     * @return 合并后的待检文本；无任何文本字段时返回 {@code ""}
     */
    private String mergeModerationText(String name, String canteenName, String stallName, String floor,
                                       Map<String, Object> attributes) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, name);
        appendIfPresent(sb, canteenName);
        appendIfPresent(sb, stallName);
        appendIfPresent(sb, floor);
        if (attributes != null && !attributes.isEmpty()) {
            appendIfPresent(sb, JsonMapUtil.toJson(attributes));
        }
        return sb.toString();
    }

    /** 追加单个非空文本字段，行间以换行分隔（首个字段不引入前置换行） */
    private void appendIfPresent(StringBuilder sb, String text) {
        if (StringUtils.hasText(text)) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(text.trim());
        }
    }

    /**
     * 纠错 UGC 文本机检：取提交人 openid 调 {@code msgSecCheck v2}（scene=2 评论场景）。
     * <p>
     * 判定口径与 feedback / review 完全一致：risky 由 {@code checkText} 统一抛 400 拦截；
     * pass 与 review（疑似）均放行。
     * <p>
     * <b>边界</b>：纠错为 permitAll 公开入口，登录态缺失时 {@code userId} 为 {@code null}
     * （如微信静默登录失败、非微信端 H5 联调），取不到 openid → {@code msgSecCheck v2}
     * 跳过机审放行，<b>由本地词库兜底</b>（{@link LocalSensitiveFilter} 已对该 name 生效）。
     */
    private void checkUgcText(Long userId, String text) {
        if (!StringUtils.hasText(text)) {
            return;   // 纯 price / images 改动，无文本可检
        }
        String openid = null;
        if (userId != null && userId != ANONYMOUS_USER_ID) {
            UserAuthContextVO user = userService.getAuthContext(userId);
            openid = user == null ? null : user.getOpenid();
        }
        contentSecurityService.checkText(openid, text, 2);
    }

    // ==================== 管理端列表（GET /admin/corrections） ====================

    @Override
    public IPage<DishCorrectionAdminVO> listForAdmin(String status, Long dishId, int page, int pageSize) {
        return listForAdmin(status, null, dishId, page, pageSize);
    }

    @Override
    public IPage<DishCorrectionAdminVO> listForAdmin(String status, String type, Long dishId,
                                                       int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        LambdaQueryWrapper<DishCorrection> wrapper = buildAdminQuery(status, type, dishId);
        IPage<DishCorrection> p = correctionMapper.selectPage(new Page<>(page, pageSize), wrapper);

        // 批量补齐提交人昵称（一次 IN 查询，消除 N+1；匿名 userId=0 不参与）——
        // 经 auth 域只读契约下发（P0-1：不再注入 UserMapper）
        List<Long> userIds = p.getRecords().stream()
                .map(DishCorrection::getUserId)
                .filter(id -> id != null && id != ANONYMOUS_USER_ID)
                .distinct()
                .toList();
        Map<Long, String> userMap = userService.mapNicknameByIds(userIds);

        // 批量补齐目标菜品名（一次 IN 查询）：只取 id/name 两列；不过滤上架态——纠错对象可能已被下架，
        // 管理端仍需看到菜品名回看内容；菜品已物理删除时不在结果集，VO 保持 null。
        Map<Long, String> dishNameMap = batchDishNames(p.getRecords());

        // 楼层连带的「同档口菜品数」：**批量**取（①批查 stallId ②按档口去重计数）——
        // 一页最多 20 行 ⇒ 查询数为「1 次 + 去重档口数」，不随行数线性增长。
        List<Long> floorDishIds = p.getRecords().stream()
                .filter(c -> StringUtils.hasText(c.getFloor()))
                .map(DishCorrection::getDishId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> stallByDish = dishService.mapStallIdByIds(floorDishIds);
        Map<Long, Long> dishCountByStall = new LinkedHashMap<>();
        for (Long stallId : new LinkedHashSet<>(stallByDish.values())) {
            dishCountByStall.put(stallId, dishService.countByStallId(stallId));
        }

        return PageUtil.toVoPage(p,
                recs -> recs.stream()
                        .map(c -> toAdminVO(c, userMap, dishNameMap, stallByDish, dishCountByStall))
                        .toList());
    }

    private LambdaQueryWrapper<DishCorrection> buildAdminQuery(String status, String type, Long dishId) {
        status = ParamValidator.optionalInWhitelist(status, CorrectionConst.QUERY_STATUSES, "处理状态");
        type = ParamValidator.optionalInWhitelist(type, CorrectionConst.QUERY_TYPES, "问题类型");
        return new LambdaQueryWrapper<DishCorrection>()
                .eq(StringUtils.hasText(status), DishCorrection::getStatus, status)
                // type 筛选：管理端按「信息有误 / 已经下架」分Tab；不传 = 全部
                .eq(StringUtils.hasText(type), DishCorrection::getType, type)
                .eq(dishId != null, DishCorrection::getDishId, dishId)
                .orderByDesc(DishCorrection::getCreatedAt);
    }

    private LambdaQueryWrapper<DishCorrection> buildAdminQuery(String status, Long dishId) {
        return buildAdminQuery(status, null, dishId);
    }

    @Override
    public long countPending() {
        return correctionMapper.selectCount(buildAdminQuery("pending", null, null));
    }

    /**
     * 待处理「已经下架」反馈数（管理端「疑似下架」聚合用）。
     * <p>
     * ⚠️<b>仅作参考展示，不是下架阈值</b> —— ≥1 条即进待办，是否下架由管理员人工决定
     * （评审问题 1 决议 5.1.4：原「≥2 独立用户」门槛已取消，那是活跃度阈值、与下架无关）。
     */
    @Override
    public long countPendingGone() {
        return correctionMapper.selectCount(
                buildAdminQuery(CorrectionConst.STATUS_PENDING, CorrectionConst.TYPE_GONE, null));
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
        // 菜品名经 dish 域只读契约下发（P0-1：不再注入 DishMapper；口径=不过滤上架态，见接口注释）
        return dishService.mapNameByIds(dishIds);
    }

    /** 管理端 VO 转换：补齐菜品名、提交人昵称、配图（相对路径 → 绝对 URL） */
    private DishCorrectionAdminVO toAdminVO(DishCorrection c, Map<Long, String> userMap, Map<Long, String> dishNameMap,
                                            Map<Long, Long> stallByDish, Map<Long, Long> dishCountByStall) {
        DishCorrectionAdminVO vo = new DishCorrectionAdminVO();
        vo.setId(c.getId());
        vo.setType(c.getType());
        vo.setDishId(c.getDishId());
        vo.setDishName(c.getDishId() != null ? dishNameMap.get(c.getDishId()) : null);
        vo.setUserId(c.getUserId());
        vo.setUserNickname(userMap.get(c.getUserId()));
        vo.setName(c.getName());
        vo.setPrice(c.getPrice());
        vo.setCanteenName(c.getCanteenName());
        vo.setStallName(c.getStallName());
        // 楼层：改动项快照，直接下发（未改动为 null）；管理端据此判断采纳时是否写回档口楼层
        vo.setFloor(c.getFloor());
        vo.setAttributes(JsonMapUtil.parseObject(c.getAttributes()));
        List<String> images = c.getImages() == null ? List.of() : c.getImages();
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        // gone 型的选填补充（管理端处置时必须展示）
        vo.setNote(c.getNote());
        vo.setStatus(c.getStatus());
        vo.setReply(c.getReply());
        vo.setRejectReason(c.getRejectReason());
        // B4 列表：给「改了几项」（不展开内容 —— 展开是详情的事）+ 是否涉及楼层
        vo.setChangeCount(changeCountOf(c));
        if (StringUtils.hasText(c.getFloor())) {
            Long stallId = stallByDish.get(c.getDishId());
            long affected = stallId == null ? 0L : dishCountByStall.getOrDefault(stallId, 0L);
            vo.setFloorImpact("将同步修改该档口下 " + affected + " 个菜品");
        }
        vo.setHandledAt(c.getHandledAt());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setUpdatedAt(c.getUpdatedAt());
        return vo;
    }

    /** 差异项数量：逐「改动项」计（属性按**维度**拆，与 {@link #buildDifferences} 的粒度一致） */
    private static int changeCountOf(DishCorrection c) {
        int count = 0;
        if (StringUtils.hasText(c.getName())) {
            count++;
        }
        if (c.getPrice() != null) {
            count++;
        }
        if (StringUtils.hasText(c.getCanteenName())) {
            count++;
        }
        if (StringUtils.hasText(c.getStallName())) {
            count++;
        }
        if (StringUtils.hasText(c.getFloor())) {
            count++;
        }
        count += JsonMapUtil.parseObject(c.getAttributes()).size();
        if (c.getImages() != null && !c.getImages().isEmpty()) {
            count++;
        }
        return count;
    }

    // ==================== 管理端详情（GET /admin/corrections/{id}，B4 差异对照） ====================

    /** 匿名提交人（`dish_correction.user_id` NOT NULL DEFAULT 0；与 `user_feedback` 同口径） */
    private static final long ANONYMOUS_USER_ID = 0L;

    /** 差异项键（= 采纳请求 `acceptedFields` 的取值） */
    private static final String FIELD_NAME = "name";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_CANTEEN_NAME = "canteenName";
    private static final String FIELD_STALL_NAME = "stallName";
    private static final String FIELD_FLOOR = "floor";
    private static final String FIELD_IMAGES = "images";
    /** 属性维度项的键前缀（`attributes.<fieldKey>`） */
    private static final String FIELD_ATTR_PREFIX = "attributes.";

    @Override
    public DishCorrectionDetailVO getDetail(Long id) {
        DishCorrection c = correctionMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        DishCorrectionDetailVO vo = new DishCorrectionDetailVO();
        vo.setId(c.getId());
        vo.setType(c.getType());
        vo.setDishId(c.getDishId());
        vo.setDishName(c.getDishId() == null ? null : dishService.mapNameByIds(List.of(c.getDishId())).get(c.getDishId()));
        vo.setUserId(c.getUserId());
        vo.setUserNickname(c.getUserId() == null || c.getUserId() == ANONYMOUS_USER_ID ? null
                : userService.mapNicknameByIds(List.of(c.getUserId())).get(c.getUserId()));
        vo.setStatus(c.getStatus());
        vo.setNote(c.getNote());
        List<String> rawImages = c.getImages() == null ? List.of() : c.getImages();
        vo.setImages(rawImages.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(rawImages));
        // gone 型**不返回差异对照**：各差异项列恒 NULL（无可对照的原值），submitted/differences 均置空，
        // 管理端只需读note + images 即可判断，处置动作恒为「下架」。
        boolean gone = CorrectionConst.TYPE_GONE.equals(c.getType());
        vo.setSubmitted(gone ? Map.of() : buildSubmitted(c, vo.getImages()));
        vo.setDifferences(gone ? List.of() : buildDifferences(c));
        if (gone) {
            vo.setGoneUserCount(countPendingGoneByDish(c.getDishId()));
        }
        vo.setReply(c.getReply());
        vo.setRejectReason(c.getRejectReason());
        vo.setHandledAt(c.getHandledAt());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    /**
     * 同菜品待处理的 gone 反馈数（<b>参考值，非下架阈值</b>）。
     * <p>
     * 用于管理端展示「有 N 人反馈已下架」；同用户对同一菜品在提交时已去重，故此处即独立用户数
     * （匿名提交无法归属、不去重会计入其中 —— 这是刻意取舍：宁可高估也不漏报）。
     */
    private Long countPendingGoneByDish(Long dishId) {
        if (dishId == null) {
            return 0L;
        }
        return correctionMapper.selectCount(new LambdaQueryWrapper<DishCorrection>()
                .eq(DishCorrection::getType, CorrectionConst.TYPE_GONE)
                .eq(DishCorrection::getDishId, dishId)
                .eq(DishCorrection::getStatus, CorrectionConst.STATUS_PENDING));
    }

    /** 用户提交的原始快照（**仅改动项**）—— 供「已同步 / 已被改回」等场景回看 */
    private Map<String, Object> buildSubmitted(DishCorrection c, List<String> images) {
        Map<String, Object> submitted = new LinkedHashMap<>();
        putIfPresent(submitted, FIELD_NAME, c.getName());
        putIfPresent(submitted, FIELD_PRICE, c.getPrice());
        putIfPresent(submitted, FIELD_CANTEEN_NAME, c.getCanteenName());
        putIfPresent(submitted, FIELD_STALL_NAME, c.getStallName());
        putIfPresent(submitted, FIELD_FLOOR, c.getFloor());
        Map<String, Object> attrs = JsonMapUtil.parseObject(c.getAttributes());
        if (!attrs.isEmpty()) {
            submitted.put("attributes", attrs);
        }
        if (!images.isEmpty()) {
            submitted.put(FIELD_IMAGES, images);
        }
        return submitted;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String s) || StringUtils.hasText(s))) {
            target.put(key, value);
        }
    }

    /**
     * 差异对照清单：**只列「仍有差异」的项**，`oldValue` 取**当前实时值**。
     * <p>
     * 快照只存「提交的新值」，原值必须回查 `dish` / `stall`（经 dish / canteen 域 Service 契约）。
     * 菜品已被物理删除 → 无从对照，返回空列表（采纳本身也会 4001）。
     */
    private List<DishCorrectionDifferenceVO> buildDifferences(DishCorrection c) {
        if (c.getDishId() == null || !dishService.existsById(c.getDishId())) {
            return List.of();
        }
        DishAdminVO live = dishService.getForAdmin(c.getDishId());
        if (live == null) {
            return List.of();
        }
        List<DishCorrectionDifferenceVO> diffs = new ArrayList<>();
        addDiff(diffs, FIELD_NAME, "菜品名称", live.getName(), c.getName(), false);
        // 单位是**分**：label 明写，避免端上与「元」混淆
        addDiff(diffs, FIELD_PRICE, "现价（分）", live.getPrice(), c.getPrice(), false);
        addDiff(diffs, FIELD_CANTEEN_NAME, "食堂名称", live.getCanteenName(), c.getCanteenName(), false);
        addDiff(diffs, FIELD_STALL_NAME, "档口名称", live.getStallName(), c.getStallName(), false);
        // 楼层归属**档口**：实时值必须回查 stall；采纳会连带同档口所有菜品 ⇒ 端上高亮提示
        if (StringUtils.hasText(c.getFloor())) {
            addDiff(diffs, FIELD_FLOOR, "楼层", stallService.getFloorById(live.getStallId()), c.getFloor(), true);
        }
        addDiff(diffs, FIELD_IMAGES, "菜品图片", live.getImages(),
                c.getImages() == null || c.getImages().isEmpty() ? null : c.getImages(), false);
        // 描述属性：按**维度**拆项（键 = attributes.<fieldKey>，可直接进 acceptedFields）
        Map<String, String> dimensionNames = dimensionNameByFieldKey();
        JsonMapUtil.parseObject(c.getAttributes()).forEach((fieldKey, submitted) -> {
            Object liveValue = live.getAttributes() == null ? null : live.getAttributes().get(fieldKey);
            String label = dimensionNames.getOrDefault(fieldKey, "描述属性");
            addDiff(diffs, FIELD_ATTR_PREFIX + fieldKey, label, liveValue, submitted, false);
        });
        return diffs;
    }

    /** 维度中文名映射（fieldKey → name）；字典读失败不阻塞详情（退化为通用文案） */
    private Map<String, String> dimensionNameByFieldKey() {
        Map<String, String> names = new LinkedHashMap<>();
        for (DishDimensionAdminVO dim : attributeAdminService.listDimensions()) {
            names.put(dim.getFieldKey(), dim.getName());
        }
        return names;
    }

    /** 加一项差异；**值相同则不加**（只列「仍有差异」的项） */
    private static void addDiff(List<DishCorrectionDifferenceVO> out, String field, String label,
                                Object oldValue, Object newValue, boolean affectsOthers) {
        String oldText = describe(oldValue);
        String newText = describe(newValue);
        if (Objects.equals(oldText, newText)) {
            return;
        }
        DishCorrectionDifferenceVO diff = new DishCorrectionDifferenceVO();
        diff.setField(field);
        diff.setLabel(label);
        diff.setOldValue(oldText);
        diff.setNewValue(newText);
        diff.setAffectsOthers(affectsOthers);
        out.add(diff);
    }

    /** 值 → 端上可读文本（集合给「共 N 项：…」，其余原样） */
    private static String describe(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> col) {
            if (col.isEmpty()) {
                return "";
            }
            return "共 " + col.size() + " 项：" + col.stream().map(String::valueOf)
                    .collect(Collectors.joining("、"));
        }
        return String.valueOf(value);
    }

    /** 逐项采纳：把 `acceptedFields` 里的属性维度项取出（只带选中维度，dish 域按维度合并） */
    private static Map<String, Object> acceptedAttributes(DishCorrection correction, Set<String> accepted) {
        Map<String, Object> picked = new LinkedHashMap<>();
        JsonMapUtil.parseObject(correction.getAttributes()).forEach((fieldKey, value) -> {
            if (accepted.contains(FIELD_ATTR_PREFIX + fieldKey)) {
                picked.put(fieldKey, value);
            }
        });
        return picked;
    }

    /**
     * 采纳清单校验：**必填非空**（空数组 → 400：什么都不采纳不是采纳，是拒绝），
     * 且每一项都必须是**此刻仍有差异**的项（否则会出现「采纳一个已同步的值」）。
     */
    private Set<String> resolveAcceptedFields(DishCorrection correction, DishCorrectionAdoptReq req) {
        List<String> raw = req == null ? null : req.getAcceptedFields();
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException("请选择要采纳的差异项（要拒绝请走拒绝接口）");
        }
        Set<String> valid = buildDifferences(correction).stream()
                .map(DishCorrectionDifferenceVO::getField)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> accepted = new LinkedHashSet<>(raw);
        List<String> illegal = accepted.stream().filter(field -> !valid.contains(field)).toList();
        if (!illegal.isEmpty()) {
            throw new BusinessException("采纳项无效或已无差异：" + String.join(", ", illegal));
        }
        return accepted;
    }

    // ==================== 管理端采纳（POST /admin/corrections/{id}/adopt，两段式档口确认） ====================

    /**
     * <b>gone 型采纳 = 仅「下架」该菜品</b>（{@code dish.status='off'}）。
     * <p>
     * 🔴 <b>红线：本方法绝不删除菜品。</b>
     * <ul>
     *   <li>只调 {@link DishService#updateStatus}（上下架，<b>可逆</b>），评价<b>完整保留</b>；</li>
     *   <li>误下架时管理员可在 A3 重新上架（已有能力），<b>无需重建菜品</b>；</li>
     *   <li>物理删除（{@code DELETE /admin/dishes/{id}}）会触发 {@code review.dish_id} 的
     *       {@code ON DELETE CASCADE}，该菜全部历史评价不可再生 ⇒ <b>绝不在本流程暴露</b>。</li>
     * </ul>
     * <p>
     * 菜品已下架时（可能已被管理员先行处理）：<b>幂等视为已完成</b>，仍归档本反馈为 adopted，
     * 不报错 —— 避免管理员「看到已下架却无法归档反馈」的困惑。
     *
     * @param reqReply 采纳附注（可选；留空用 {@link CorrectionConst#GONE_ADOPT_REPLY} 固定文案随回执下发）
     * @return 恒为 {@code null}（gone 型无档口确认环节）
     */
    private StallConfirmVO adoptGone(DishCorrection correction, String reqReply) {
        // 已下架 ⇒ 无需重复写；仍走归档（status/reply/handledAt）+ 回执
        if (dishService.existsOnSale(correction.getDishId())) {
            dishService.updateStatus(correction.getDishId(), DishConst.STATUS_OFF);
            log.info("[菜品问题反馈] gone 型采纳→下架：dishId={}, correctionId={}",
                    correction.getDishId(), correction.getId());
        }
        String reply = StringUtils.hasText(reqReply) ? reqReply : CorrectionConst.GONE_ADOPT_REPLY;
        // 归档（与 field 型 adopt 同一套字段写法，不另立口径）
        correction.setStatus(CorrectionConst.STATUS_ADOPTED);
        correction.setReply(reply);
        correction.setRejectReason(null);
        correction.setHandledAt(LocalDateTime.now());
        correctionMapper.updateById(correction);
        // 回执走 gone 专用文案（「已下架，感谢反馈」），附注非空时追加
        sendCorrectionReceipt(correction, CorrectionConst.STATUS_ADOPTED, true, reply, null, null, null);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StallConfirmVO adopt(Long id, DishCorrectionAdoptReq req) {
        DishCorrection correction = correctionMapper.selectById(id);
        if (correction == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        // 前置：status=pending，否则 400（幂等：重复调用 = 已处理 →「该反馈已处理」）
        if (!CorrectionConst.STATUS_PENDING.equals(correction.getStatus())) {
            throw new BusinessException(400, "该反馈已处理");
        }
        // 目标菜品物理删除 → 4001（采纳无从写回）；存在性口径由 dish 域下发（P0-1）
        if (!dishService.existsById(correction.getDishId())) {
            throw new BusinessException(4001, "菜品不存在，无法采纳");
        }

        // ---- gone（已经下架）分派：采纳动作 = **仅下架** ----
        // 🔴 红线：此处**只调 updateStatus(off)**，绝不触碰任何物理删除路径。
        //理由：gone 反馈天然含误报（看错窗口 / 临时售罄），若允许删除则「一次误报 + 一次点错」
        //      = 该菜全部历史评价永久消失（review.dish_id ON DELETE CASCADE，不可再生）。
        if (CorrectionConst.TYPE_GONE.equals(correction.getType())) {
            return adoptGone(correction, req == null ? null : req.getReply());
        }

        // ---- 逐项采纳清单（B4）：必填非空，且每项必须是「此刻仍有差异」的项 ----
        Set<String> accepted = resolveAcceptedFields(correction, req);
        // 只有采纳了**档口 / 食堂项**才需要两段式档口确认（楼层与档口挂靠是两件独立的事）
        boolean acceptedStall = accepted.contains(FIELD_CANTEEN_NAME) || accepted.contains(FIELD_STALL_NAME);

        // ---- 两段式档口确认：解析最终挂靠档口（仅在采纳了档口/食堂项时） ----
        Long resolvedStallId = null;
        if (acceptedStall) {
            if (req != null && req.getStallId() != null) {
                // 第二段（管理端选定既有档口）：显式 stallId 优先，校验存在后挂靠
                if (!stallService.existsById(req.getStallId())) {
                    throw new BusinessException(400, "档口不存在");
                }
                resolvedStallId = req.getStallId();
            } else {
                Long matchedStallId = stallService.findIdByName(correction.getStallName());
                if (matchedStallId != null) {
                    // 提交档口名与现有档口归一化精确匹配命中 → 直接采纳
                    resolvedStallId = matchedStallId;
                } else if (req != null && Boolean.TRUE.equals(req.getCreateIfMissing())) {
                    // 第二段（确认新建）：按提交档口名 upsert（所属食堂按提交食堂名 upsert，
                    // 空值语义名称 → 400「请选择所属食堂」，与菜品录入编辑同口径）
                    resolvedStallId = stallService.upsertStallByName(correction.getStallName(),
                            correction.getCanteenName());
                } else {
                    // 未命中且未指定档口/未确认新建 → 不执行采纳，返回候选档口供管理端选择
                    return new StallConfirmVO(true, buildCandidates(correction.getCanteenName()));
                }
            }
        }

        // 部分采纳须**逐项告知**已采纳 / 未采纳字段中文名（否则用户会重复提交同一项）。
        // 差异清单必须在**写回之前**取：写回后各项已与菜品一致，buildDifferences 会返回空。
        List<DishCorrectionDifferenceVO> allDiffs = buildDifferences(correction);
        List<String> adoptedLabels = labelsOf(allDiffs, accepted);
        List<String> rejectedLabels = allDiffs.stream()
                .filter(d -> !accepted.contains(d.getField()))
                .map(DishCorrectionDifferenceVO::getLabel)
                .toList();
        boolean partial = adoptedLabels.size() < allDiffs.size();

        String actualReply = applyAdoption(correction, resolvedStallId, accepted, req);
        // 站内回执（提交人非空时投递，失败不阻塞采纳）
        // 落库 `reply` 缺省用固定文案；回执正文只在**管理员确实填了附注**时追加，避免复述固定文案
        String adminReply = req == null ? null : req.getReply();
        sendCorrectionReceipt(correction, CorrectionConst.STATUS_ADOPTED, false,
                adminReply == null ? null : adminReply.trim(),
                partial ? adoptedLabels : null, partial ? rejectedLabels : null, null);
        // 采纳已执行：无档口确认环节，返回值恒为 null
        return null;
    }

    /** 采纳清单 → 字段中文名（供部分采纳回执逐项告知；顺序与差异清单一致） */
    private static List<String> labelsOf(List<DishCorrectionDifferenceVO> diffs, Set<String> accepted) {
        return diffs.stream()
                .filter(d -> accepted.contains(d.getField()))
                .map(DishCorrectionDifferenceVO::getLabel)
                .toList();
    }

    /**
     * 采纳写回：七字段写回 dish（name/price/档口挂靠/flavorTags/ingredients/images）——
     * 可空快照字段（flavorTags/ingredients/images）不覆盖既有值（MyBatis-Plus NOT_NULL 策略跳过 null），
     * 保护「菜品首图必填」等既有不变量；随后纠错记录归档（status/reply/handled_at，
     * stall_name 以实际挂靠档口名落库，管理端指定档口可能不同于提交名）。
     * <p>
     * P0-1：写回动作经 {@link DishService#applyCorrection(DishCorrectionCmd)} 下发——correction
     * 不再构造 {@code Dish} 实体、不再注入 DishMapper，images 的 JSON 序列化与 null 跳过策略
     * 属 dish 域落库形态，一并收回 dish 实现。
     * <p>
     * <b>楼层纠错</b>：{@code floor} <b>不写回 dish</b>——楼层归属<b>档口</b>
     * （{@code stall.floor}），菜品无楼层字段。故本次纠错携带楼层时，写入上面已解析出的
     * <b>目标档口</b>（{@code resolvedStallId}），同档口下全部菜品的详情楼层一并生效；
     * 写动作经 {@code StallService#updateFloor} 跨域写契约下发（同 P0-1 收口口径）。
     */
    /**
     * 单条：目标菜品**当前**所属档口（只采纳 floor 时的楼层落点）。
     * 单条路径不存在 N+1，故直接取菜品全字段（列表路径才走 {@code mapStallIdByIds} 批量投影）。
     */
    private Long currentStallIdOf(Long dishId) {
        if (dishId == null) {
            return null;
        }
        DishAdminVO live = dishService.getForAdmin(dishId);
        return live == null ? null : live.getStallId();
    }

    private String applyAdoption(DishCorrection correction, Long resolvedStallId, Set<String> accepted,
                                 DishCorrectionAdoptReq req) {
        // 楼层改动项：**采纳项含 floor** 才写回（归属档口 stall.floor，同档口其他菜品一并生效）。
        // 目标档口 = 本次解析结果；未采纳档口项时 = 该菜**当前**所属档口（楼层与档口挂靠独立）。
        if (accepted.contains(FIELD_FLOOR) && StringUtils.hasText(correction.getFloor())) {
            Long targetStallId = resolvedStallId == null ? currentStallIdOf(correction.getDishId()) : resolvedStallId;
            if (targetStallId != null) {
                stallService.updateFloor(targetStallId, correction.getFloor());
            }
        }
        // 实际挂靠档口名（管理端指定档口可能不同于提交名；未采纳档口项时为 null，不归档改名）
        String resolvedStallName = resolvedStallId == null ? null : stallService.getNameById(resolvedStallId);
        // 逐项写回：只带**选中项**，未选中项一律 null（dish 域 NOT_NULL 策略跳过 ⇒ 不覆盖既有值）
        String name = accepted.contains(FIELD_NAME) ? correction.getName() : null;
        Integer price = accepted.contains(FIELD_PRICE) ? correction.getPrice() : null;
        List<String> images = accepted.contains(FIELD_IMAGES) ? correction.getImages() : null;
        Map<String, Object> attributes = acceptedAttributes(correction, accepted);
        if (!dishService.applyCorrection(new DishCorrectionCmd(correction.getDishId(), name, price,
                resolvedStallId, attributes, images))) {
            // 并发删除兜底（采纳前置已查到菜品）
            throw new BusinessException(4001, "菜品不存在，无法采纳");
        }

        // 采纳回复：附注优先（≤600 已在 DTO 校验），缺省固定文案
        String reply = req != null && StringUtils.hasText(req.getReply())
                ? req.getReply().trim() : CorrectionConst.ADOPT_REPLY;
        correction.setStatus(CorrectionConst.STATUS_ADOPTED);
        correction.setReply(reply);
        correction.setRejectReason(null);
        correction.setHandledAt(LocalDateTime.now());
        // 归档实际挂靠档口名（**null-safe，缺陷修复**）：局部提交下 stallName 快照可为 null
        // （用户未改动档口名），原写法 correction.getStallName().equals(...) 在该情形直接 NPE（采纳 500）。
        // 楼层纠错让这条路径从「罕见」变成常态（仅改楼层的提交 stallName 恒为 null），故必须收口。
        // 语义不变：resolvedStallName 非空且与快照不同（快照为 null 亦属「不同」）时，以实际挂靠档口名归档。
        if (resolvedStallName != null && !resolvedStallName.equals(correction.getStallName())) {
            correction.setStallName(resolvedStallName);
        }
        correctionMapper.updateById(correction);
        return reply;
    }

    /**
     * 构建档口确认候选列表：提交食堂名匹配现有食堂时 = 该食堂下全部档口（sort_order 升序）；
     * 无匹配食堂时 = 全量档口（canteen_id 升序 → sort_order 升序，与既有档口排序口径一致）。
     */
    private List<StallConfirmVO.StallCandidate> buildCandidates(String canteenName) {
        // 候选档口取数（含「按名找食堂」）经 canteen 域只读契约下发（P0-1：不再注入 CanteenMapper/StallMapper），
        // 两分支的排序口径与原实现逐条一致；本域只负责组装自己的 VO。
        return stallService.listBriefCandidates(canteenName).stream()
                .map(s -> new StallConfirmVO.StallCandidate(s.getId(), s.getName()))
                .toList();
    }

    // ==================== 管理端拒绝（PUT /admin/corrections/{id}） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, DishCorrectionHandleReq req) {
        DishCorrection correction = correctionMapper.selectById(id);
        if (correction == null) {
            throw new BusinessException(4001, "纠错不存在");
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
        // reply **可选**（B4，2026-10-03）：DTO 仅卡长度，Service 不再拦必填；
        // 留空时回执正文退化为「不采纳原因」（必填项），保证提交人始终收到可读内容。
        String trimmedReply = req.getReply() == null ? null : req.getReply().trim();
        // 上限 600 字（回执正文 = 前缀 + 回复全文，须 ≤ notification.content 的 1024 列宽）
        if (trimmedReply != null && trimmedReply.length() > CorrectionConst.REPLY_MAX_LENGTH) {
            throw new BusinessException(400, "处理回复不能超过" + CorrectionConst.REPLY_MAX_LENGTH + "字");
        }
        // 不采纳原因必填（与反馈处理同源口径）：1~200 字，纯空白视为未填写 → 400
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
        // 站内回执（携带不采纳原因与处理说明；gone 型走「经核实，该菜品仍在售」文案）
        boolean gone = CorrectionConst.TYPE_GONE.equals(correction.getType());
        sendCorrectionReceipt(correction, CorrectionConst.STATUS_REJECTED, gone,
                trimmedReply, null, null, rejectReason);
    }

    // ==================== 站内回执 ====================

    /**
     * 纠错处理回执（采纳 / 下架 / 驳回统一入口，参考 feedback handle 通知实现）。
     * <p>
     * 归属判据：提交时带 userId（登录态）即投递 —— **不按邮箱认证过滤**（消息中心为登录级能力，
     * 游客提交的反馈同样保留可回执身份）。
     * 投递失败不影响处理结果（独立 try 分支，异常不外抛到主流程）。
     * <p>
     * **标题与正文按结论分派**（文案口径见 B4 功能文档「回执文案」）：
     * <ul>
     *   <li>`field` 采纳（全部项）→「菜品信息已更新」；</li>
     *   <li>`field` 采纳（部分项）→ 同标题，正文**逐项列出已采纳 / 未采纳字段中文名**
     *       （用户提交 3 项只采纳 2 项时必须知道哪一项没被采纳，否则会重复提交）；</li>
     *   <li>`field` 驳回 →「菜品信息未采纳」；</li>
     *   <li>`gone` 下架 →「菜品已下架」；`gone` 驳回 →「菜品仍在售」。</li>
     * </ul>
     *
     * @param outcome 处理结论：`adopted`（采纳 / 下架）或 `rejected`（驳回）
     * @param gone    本条反馈是否 {@code type=gone}（决定文案分派）
     * @param reply   处理说明（缺省文案已在各调用点定好；`null` 表示不追加）
     * @param adoptedLabels 已采纳字段中文名（`field` 型部分采纳时非空）
     * @param rejectedLabels 未采纳字段中文名（`field` 型部分采纳时非空）
     * @param rejectReason 不采纳原因（`outcome=rejected` 时非空）
     */
    private void sendCorrectionReceipt(DishCorrection correction, String outcome, boolean gone,
                                       String reply, List<String> adoptedLabels,
                                       List<String> rejectedLabels, String rejectReason) {
        Long userId = correction.getUserId();
        if (userId == null || userId == ANONYMOUS_USER_ID) {
            return;
        }
        try {
            String body = receiptBody(outcome, gone, reply, adoptedLabels, rejectedLabels, rejectReason);
            notificationService.notify(new NotificationCmd(userId, receiptTitle(outcome, gone), body));
        } catch (Exception ignored) {
            // 回执失败不阻塞处理流程
            //
            // 边界说明：与 FeedbackServiceImpl 同源 —— 本 catch 只拦得住「提交任务」阶段的异常，
            // 拦不住「异步线程内写库失败」（@Async 异常不回传）。真正的失败由
            // NotificationServiceImpl#notify 内部 catch 记 error 日志，不静默。
        }
    }

    /** 回执标题（按结论 + `type` 分派；口径见 B4 功能文档「回执文案」） */
    private static String receiptTitle(String outcome, boolean gone) {
        if (gone) {
            return CorrectionConst.OUTCOME_REJECTED.equals(outcome) ? "菜品仍在售" : "菜品已下架";
        }
        return CorrectionConst.OUTCOME_REJECTED.equals(outcome) ? "菜品信息未采纳" : "菜品信息已更新";
    }

    /** 回执正文（按结论 + `type` 分派；`reply` 非空时以「。处理说明：」追加） */
    private static String receiptBody(String outcome, boolean gone, String reply,
                                      List<String> adoptedLabels, List<String> rejectedLabels,
                                      String rejectReason) {
        String main;
        if (CorrectionConst.OUTCOME_REJECTED.equals(outcome)) {
            main = gone
                    ? CorrectionConst.GONE_REJECT_REPLY + "：" + rejectReason
                    : "你提交的菜品信息未采纳：" + rejectReason;
        } else if (gone) {
            main = CorrectionConst.GONE_ADOPT_REPLY;
        } else if (adoptedLabels != null && !adoptedLabels.isEmpty()) {
            // 部分采纳：逐项告知（采纳了哪些、没采纳哪些），避免用户重复提交
            main = "你提交的菜品信息纠错已采纳：" + String.join("、", adoptedLabels)
                    + (rejectedLabels == null || rejectedLabels.isEmpty()
                    ? "" : "；未采纳：" + String.join("、", rejectedLabels));
        } else {
            main = "你提交的菜品信息纠错已采纳，菜品信息已更新";
        }
        return StringUtils.hasText(reply) ? main + "。处理说明：" + reply : main;
    }
}
