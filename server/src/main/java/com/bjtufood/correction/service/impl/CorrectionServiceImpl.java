package com.bjtufood.correction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.canteen.constant.FloorDict;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonMapUtil;
import com.bjtufood.common.utils.PageUtil;
import com.bjtufood.common.utils.ParamValidator;
import com.bjtufood.correction.constant.CorrectionConst;
import com.bjtufood.correction.dto.CorrectionAdminVO;
import com.bjtufood.correction.dto.CorrectionDetailVO;
import com.bjtufood.correction.dto.DishCorrectionDifferenceVO;
import com.bjtufood.correction.dto.DishCorrectionHandleReq;
import com.bjtufood.correction.dto.DishCorrectionReq;
import com.bjtufood.correction.entity.Correction;
import com.bjtufood.correction.mapper.CorrectionMapper;
import com.bjtufood.correction.service.CorrectionService;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDimensionAdminVO;
import com.bjtufood.dish.service.DishAttributeAdminService;
import com.bjtufood.dish.service.DishService;
import com.bjtufood.moderation.constant.ModerationConst;
import com.bjtufood.moderation.dto.ModerationHandleReq;
import com.bjtufood.moderation.dto.ModerationTaskVO;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.moderation.service.ModerationTaskService;
import com.bjtufood.moderation.support.ModerationTaskFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜品问题反馈服务实现（独立资源：POST /dishes/{id}/correction + /admin/corrections）。
 * <p>
 * **提交按 {@code type} 分派**：`field`（信息有误）走局部提交（patch）——只落库改动项
 * （食堂名 / 档口名为自由文本；楼层为受控字典值，值即汉字）；`gone`（已经下架）走一键提交。
 * 提交行与处置任务行在**同一事务**内落库（见 {@link CorrectionPersister#insert}）。
 * <p>
 * **管理端处置不改写菜品**：管理员「阅读」反馈后，统一两个动作 ——
 * <ul>
 *   <li>**标记已处理**（接受）：归档 {@code outcome='handled'}（已受理，管理员已手动处理）+ 回执；</li>
 *   <li>**驳回**：归档 {@code outcome='rejected'}，处置文案即不采纳原因（缺省标题按类型给
 *       「菜品信息未采纳」/「菜品仍在售」）。</li>
 * </ul>
 * 两者都只经 {@code ModerationTaskService#handle} 归档并投递站内回执，**均不写 dish / stall**；
 * 菜品资料修改 / {@code gone} 可逆下架由管理员在菜品管理（web/dishes）手动执行。
 * 两种处置结论的缺省文案由本域按业务措辞下发（**唯一文案列** {@code reply_content}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrectionServiceImpl implements CorrectionService {

    private final CorrectionMapper correctionMapper;
    /**
     * 落库事务边界：{@code submit} **只让落库一步进事务**（「先机审（无事务）→ 再落库（开事务）」）——
     * 若事务从方法入口开始，会横跨微信机审的 HTTP 外呼（超时 5s），期间持续占用数据库连接；
     * HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
     * <p>
     * 必须是**独立 Bean**：Spring 事务靠代理生效，同类自调用不会开启事务。
     */
    private final CorrectionPersister correctionPersister;
    /** 跨域只读契约：菜品存在性/在售判定、菜品名投影、详情提交项对照的当前值（P0-1，替代 DishMapper 直连） */
    private final DishService dishService;
    /** 跨域只读契约（dish.service 接口）：维度字典，供提交项对照下发**维度中文名**（端上零硬编码） */
    private final DishAttributeAdminService attributeAdminService;
    /** 跨域只读契约：档口楼层当前值（详情提交项对照用） */
    private final StallService stallService;
    /** 跨域只读契约：管理端「提交人」昵称投影 + 内容安检取 openid（P0-1，替代 UserMapper 直连） */
    private final UserService userService;
    private final LocalSensitiveFilter localSensitiveFilter;
    /** 微信内容安全检测 */
    private final ContentSecurityService contentSecurityService;
    private final ImageUrlUtil imageUrlUtil;
    /** 处置状态机（`moderation_task`）：提交预建、处置归档 + 回执投递、列表批量回填 */
    private final ModerationTaskService moderationTaskService;

    // ==================== 提交（POST /dishes/{id}/correction） ====================

    /**
     * 提交纠错。<b>本方法刻意不加 {@code @Transactional}</b>：事务边界收窄到落库一步
     * （{@link CorrectionPersister#insert}），使微信机审的外呼期间不占用数据库连接。
     * <p>
     * 注：{@code handle} / {@code reject} / {@code listForAdmin} 不含外部 HTTP 调用，事务边界不受此约束。
     */
    @Override
    public void submit(Long userId, Long dishId, DishCorrectionReq req) {
        // correction.user_id 必填真实登录用户（外键 fk_correction_user → user(id)）：无登录态即 401
        requireLogin(userId);
        // 菜品不存在与已下架同款处理（对公开接口而言「下架」等价于「不存在」，与 DishServiceImpl 详情口径一致）；
        // 存在性 + 在售态口径由 dish 域唯一持有（P0-1：本域不 import Dish 实体/DishConst/DishMapper）
        if (!dishService.existsOnSale(dishId)) {
            throw new BusinessException(4001, "菜品不存在");
        }
        // type 分派：gone（已经下架）走「一键提交」路径，其余走 field 的局部提交路径。
        // 两条路径的字段集合、校验规则、落库内容完全不同，故在此处分流而非混在一段校验里。
        if (CorrectionConst.TYPE_GONE.equals(req.getType())) {
            submitGone(userId, dishId, req);
            return;
        }
        submitField(userId, dishId, req);
    }

    /** 登录前置：correction 的提交人恒为真实登录用户（user_id 必填 + 外键）。 */
    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    /**
     * <b>type=gone（已经下架）· 一键提交</b>。
     * <p>
     * 核心约束：<b>note / images 均为选填，允许全不传</b>——提交即成立，这是「用户是事实校对员」
     * 这一产品定位的落地边界；一旦要求必填，用户成本从「点一下」回升到「填表」，提交量将大幅下降。
     * <p>
     * 同时<b>拒绝任何差异项字段</b>：那些字段表达的是「这道菜存在，只是信息写错了」，
     * 与「这道菜消失了」语义冲突；用户想补录信息应改选 {@code field} 型（或由管理员在菜品管理直接编辑）。
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

        // 去重：同一用户 + 同一菜品已有一条 gone 反馈（不限定处理状态）⇒ 直接返回成功，不重复计数
        if (existsGoneByUser(userId, dishId)) {
            log.info("[菜品问题反馈] gone 型重复提交已忽略：userId={}, dishId={}", userId, dishId);
            return;
        }

        Correction correction = new Correction();
        correction.setType(CorrectionConst.TYPE_GONE);
        correction.setDishId(dishId);
        correction.setUserId(userId);
        // gone 行各差异项列恒 NULL —— 语义是「无可对照的原值」，不是「未改动」
        correction.setNote(note);
        correction.setImages(images);
        correctionPersister.insert(correction);
    }

    /**
     * 是否已存在同一用户对同一菜品的 gone 型反馈（**不限定处理状态**）。
     * <p>
     * 去重键即 {@code user_id}：提交入口已要求登录态，同一登录用户对同一菜品恒只计一次。
     * <p>
     * 不限定处置状态：某条已处理 / 已驳回后，再次提交也<b>不新增行</b>。去重是<b>防单人刷队列</b>，
     * 不是决策门槛（≥1 条即进待办，是否下架由管理员人工决定）。
     *
     * @param userId 提交人用户ID（登录态，非空）
     * @param dishId 菜品
     * @return true = 已有记录，本次不重复落库
     */
    private boolean existsGoneByUser(Long userId, Long dishId) {
        LambdaQueryWrapper<Correction> wrapper = new LambdaQueryWrapper<Correction>()
                .eq(Correction::getType, CorrectionConst.TYPE_GONE)
                .eq(Correction::getDishId, dishId)
                .eq(Correction::getUserId, userId);
        return correctionMapper.exists(wrapper);
    }

    /**
     * <b>type=field（信息有误）· 局部提交</b>。
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
        // floor：楼层（受控字典·值即汉字，归属档口），传入即校验 ∈ 字典。
        // 长度上限与 stall.floor VARCHAR(16) 一致：该提交值会作为阅读依据呈现在管理端，
        // 管理员据此在 A2 档口管理手动修改 stall.floor。
        // 值域见 FloorDict（负一层 / 一层 / 二层 / 三层 / 四层），字典外值 → 400。
        String floor = null;
        if (req.getFloor() != null) {
            floor = FloorDict.normalize(req.getFloor());
            if (!StringUtils.hasText(floor)) {
                throw new BusinessException(400, "楼层不能为空");
            }
            if (floor.length() > CorrectionConst.FLOOR_MAX_LENGTH) {
                throw new BusinessException(400, "楼层超长");
            }
            if (!FloorDict.isValid(floor)) {
                throw new BusinessException(400, "楼层不在可选范围：负一层 / 一层 / 二层 / 三层 / 四层");
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

        // floor 计入改动项：**仅改楼层**的一次提交（如「一层」→「二层」）也是有效纠错，不得判「未提交任何改动」
        if (name == null && price == null && canteenName == null && stallName == null && floor == null
                && attributes == null && images == null) {
            throw new BusinessException(400, "未提交任何改动");
        }

        // ---- 微信内容安全检测----
        // 纠错的 name / canteenName / stallName（自由文本）与 floor（受控字典值）及 attributes（中文值）
        // 都是用户可提交内容，且会随反馈进入管理端人工阅读与处置（管理员据此在 A3 手动录入菜品），
        // 仅靠本地静态词库不足以覆盖谐音/变体/语义违规。
        // 口径与 feedback / review / 昵称一致：risky 由 checkText 统一拦截为 400。
        // 合并为**单次**调用送检（见 mergeModerationText）：msgSecCheck 按调用计费且有 2500 字上限，
        // 分字段多次送检会成倍放大微信调用额度，故拼接后一次提交。
        checkUgcText(userId, mergeModerationText(name, canteenName, stallName, floor, attributes));

        Correction correction = new Correction();
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
     * @param floor 楼层（受控字典值，归属档口；随反馈进入管理端人工阅读，
     *              故与其余文本字段同等待遇，一并送检）
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
     * <b>送检身份</b>：提交人必为真实登录用户（见 {@link #requireLogin}），openid 由其账号取出；
     * 账号无 openid（历史学号账号）时 {@code checkText} 按既有 fail-closed 口径拒绝写入，
     * 绝不静默放行未过检文本。
     */
    private void checkUgcText(Long userId, String text) {
        if (!StringUtils.hasText(text)) {
            return;   // 纯 price / images 改动，无文本可检
        }
        UserAuthContextVO user = userService.getAuthContext(userId);
        String openid = user == null ? null : user.getOpenid();
        contentSecurityService.checkText(openid, text, 2);
    }

    // ==================== 管理端列表（GET /admin/corrections） ====================

    @Override
    public IPage<CorrectionAdminVO> listForAdmin(String status, String type, Long dishId,
                                                 int page, int pageSize) {
        int[] norm = PageUtil.normalize(page, pageSize);
        page = norm[0];
        pageSize = norm[1];

        LambdaQueryWrapper<Correction> wrapper = buildAdminQuery(status, type, dishId);
        IPage<Correction> p = correctionMapper.selectPage(new Page<>(page, pageSize), wrapper);

        // 批量补齐提交人昵称（一次 IN 查询，消除 N+1）——
        // 经 auth 域只读契约下发（P0-1：本域不注入 UserMapper）
        List<Long> userIds = p.getRecords().stream()
                .map(Correction::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> userMap = userService.mapNameByIds(userIds);

        // 批量补齐目标菜品名（一次 IN 查询）：只取 id/name 两列；不过滤上架态——纠错对象可能已被下架，
        // 管理端仍需看到菜品名回看内容；菜品已物理删除时不在结果集，VO 保持 null。
        Map<Long, String> dishNameMap = batchDishNames(p.getRecords());

        // 处置结果（处置状态 / 结论 / 处置文案 / 处置人）：按本页 ID 集合批量回填
        Set<Long> ids = new HashSet<>();
        p.getRecords().forEach(c -> ids.add(c.getId()));
        Map<Long, ModerationTaskVO> tasks =
                moderationTaskService.mapByTypeAndIds(ModerationConst.TYPE_CORRECTION, ids);

        return PageUtil.toVoPage(p,
                recs -> recs.stream()
                        .map(c -> toAdminVO(c, userMap, dishNameMap, tasks))
                        .toList());
    }

    private LambdaQueryWrapper<Correction> buildAdminQuery(String status, String type, Long dishId) {
        String normalizedStatus = ParamValidator.optionalInWhitelist(status, ModerationConst.STATUSES, "处置状态");
        String normalizedType = ParamValidator.optionalInWhitelist(type, CorrectionConst.QUERY_TYPES, "问题类型");
        LambdaQueryWrapper<Correction> wrapper = new LambdaQueryWrapper<Correction>()
                .eq(StringUtils.hasText(normalizedType), Correction::getType, normalizedType)
                .eq(dishId != null, Correction::getDishId, dishId);
        // 「是否已处置」是**处置任务表**的状态：条件片段由 moderation 域下发（该域持有该表的 SQL 知识）
        if (StringUtils.hasText(normalizedStatus)) {
            ModerationTaskFilters.byStatus(wrapper, ModerationConst.TYPE_CORRECTION, "correction.id", normalizedStatus);
        }
        return wrapper.orderByDesc(Correction::getCreatedAt);
    }

    /**
     * 批量查询本页纠错目标菜品名：dishId 去重后一次 IN 查询，空集合返回空 Map（不发起查询）
     */
    private Map<Long, String> batchDishNames(List<Correction> records) {
        List<Long> dishIds = records.stream()
                .map(Correction::getDishId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        // 菜品名经 dish 域只读契约下发（P0-1：本域不注入 DishMapper；口径=不过滤上架态，见接口注释）
        return dishService.mapNameByIds(dishIds);
    }

    /** 管理端 VO 转换：补齐菜品名、提交人昵称、配图（相对路径 → 绝对 URL）与处置结果 */
    private CorrectionAdminVO toAdminVO(Correction c, Map<Long, String> userMap, Map<Long, String> dishNameMap,
                                        Map<Long, ModerationTaskVO> tasks) {
        CorrectionAdminVO vo = new CorrectionAdminVO();
        vo.setId(c.getId());
        vo.setType(c.getType());
        vo.setDishId(c.getDishId());
        vo.setDishName(c.getDishId() != null ? dishNameMap.get(c.getDishId()) : null);
        vo.setUserId(c.getUserId());
        vo.setUserName(userMap.get(c.getUserId()));
        vo.setName(c.getName());
        vo.setPrice(c.getPrice());
        vo.setCanteenName(c.getCanteenName());
        vo.setStallName(c.getStallName());
        // 楼层：提交项快照，直接下发（未提交为 null）；属档口属性，管理员阅读后如需改楼层在 A2 手动处理
        vo.setFloor(c.getFloor());
        vo.setAttributes(JsonMapUtil.parseObject(c.getAttributes()));
        List<String> images = c.getImages() == null ? List.of() : c.getImages();
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        // gone 型的选填补充（管理端处置时必须展示）
        vo.setNote(c.getNote());
        vo.setCreatedAt(c.getCreatedAt());
        applyTask(vo, tasks.get(c.getId()));
        return vo;
    }

    /** 处置结果回填（任务行缺失时保持 null，不伪造成「未处置」） */
    private static void applyTask(CorrectionAdminVO vo, ModerationTaskVO task) {
        if (task == null) {
            return;
        }
        vo.setStatus(task.getStatus());
        vo.setOutcome(task.getOutcome());
        vo.setReplyTitle(task.getReplyTitle());
        vo.setReplyContent(task.getReplyContent());
        vo.setHandledByName(task.getHandledByName());
        vo.setHandledAt(task.getHandledAt());
    }

    // ==================== 管理端详情（GET /admin/corrections/{id}，提交项对照） ====================

    /** 提交项对照的键（`differences[].field` 取值） */
    private static final String FIELD_NAME = "name";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_CANTEEN_NAME = "canteenName";
    private static final String FIELD_STALL_NAME = "stallName";
    private static final String FIELD_FLOOR = "floor";
    private static final String FIELD_IMAGES = "images";
    /** 属性维度项的键前缀（`attributes.<维度ID>`） */
    private static final String FIELD_ATTR_PREFIX = "attributes.";

    @Override
    public CorrectionDetailVO getDetail(Long id) {
        Correction c = correctionMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        CorrectionDetailVO vo = new CorrectionDetailVO();
        vo.setId(c.getId());
        vo.setType(c.getType());
        vo.setDishId(c.getDishId());
        // 目标菜品名与**当前 status** 实时回查（含已下架；菜品已物理删除时为 null）：
        // 单条详情无 N+1，直接取管理端 VO；先 existsById 以避开 getForAdmin 的 4001（删除后仍要能看记录）。
        String dishName = null;
        String dishStatus = null;
        if (c.getDishId() != null && dishService.existsById(c.getDishId())) {
            DishAdminVO live = dishService.getForAdmin(c.getDishId());
            dishName = live.getName();
            dishStatus = live.getStatus();
        }
        vo.setDishName(dishName);
        vo.setDishStatus(dishStatus);
        vo.setUserId(c.getUserId());
        vo.setUserName(c.getUserId() == null ? null
                : userService.mapNameByIds(List.of(c.getUserId())).get(c.getUserId()));
        vo.setNote(c.getNote());
        List<String> rawImages = c.getImages() == null ? List.of() : c.getImages();
        vo.setImages(rawImages.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(rawImages));
        // gone 型**不返回差异对照**：各差异项列恒 NULL（无可对照的原值），differences 置空，
        // 管理端只需读 note + images 即可判断，处置动作恒为「下架」。
        boolean gone = CorrectionConst.TYPE_GONE.equals(c.getType());
        vo.setDifferences(gone ? List.of() : buildDifferences(c));
        if (gone) {
            vo.setGoneUserCount(countPendingGoneByDish(c.getDishId()));
        }
        vo.setCreatedAt(c.getCreatedAt());
        // 处置结果来自处置任务表（超管处置后同一个抽屉只读态直接回显）
        ModerationTaskVO task = moderationTaskService.findByTypeAndId(ModerationConst.TYPE_CORRECTION, id);
        if (task != null) {
            vo.setStatus(task.getStatus());
            vo.setOutcome(task.getOutcome());
            vo.setReplyTitle(task.getReplyTitle());
            vo.setReplyContent(task.getReplyContent());
            vo.setHandledByName(task.getHandledByName());
            vo.setHandledAt(task.getHandledAt());
        }
        return vo;
    }

    /**
     * 同菜品**待处置**的 gone 反馈数（<b>参考值，非下架阈值</b>）。
     * <p>
     * 用于管理端展示「有 N 人反馈已下架」；同 {@code user_id} 对同一菜品在提交时已去重，
     * 故此处即独立 {@code user_id} 数。「待处置」判据 = 处置任务行仍为 {@code pending}。
     */
    private Long countPendingGoneByDish(Long dishId) {
        if (dishId == null) {
            return 0L;
        }
        LambdaQueryWrapper<Correction> wrapper = new LambdaQueryWrapper<Correction>()
                .eq(Correction::getType, CorrectionConst.TYPE_GONE)
                .eq(Correction::getDishId, dishId);
        ModerationTaskFilters.byStatus(wrapper, ModerationConst.TYPE_CORRECTION, "correction.id",
                ModerationConst.STATUS_PENDING);
        return correctionMapper.selectCount(wrapper);
    }

    /**
     * 提交项对照清单：**只列「用户提交过的项」**，`oldValue` 取**当前实时值**，`same` 标记是否一致。
     * <p>
     * 快照只存「提交的新值」（未提交列留 NULL），原值回查 `dish` / `stall`（经 dish / canteen 域 Service 契约）。
     * 菜品已被物理删除 → `oldValue` 为 null（无从对照，仅显示提交值）。
     */
    private List<DishCorrectionDifferenceVO> buildDifferences(Correction c) {
        DishAdminVO live = null;
        if (c.getDishId() != null && dishService.existsById(c.getDishId())) {
            live = dishService.getForAdmin(c.getDishId());
        }
        List<DishCorrectionDifferenceVO> diffs = new ArrayList<>();
        addDiff(diffs, FIELD_NAME, "菜品名称", live == null ? null : live.getName(), c.getName());
        // 单位是**分**：label 明写，避免端上与「元」混淆
        addDiff(diffs, FIELD_PRICE, "现价（分）", live == null ? null : live.getPrice(), c.getPrice());
        addDiff(diffs, FIELD_CANTEEN_NAME, "食堂名称", live == null ? null : live.getCanteenName(), c.getCanteenName());
        addDiff(diffs, FIELD_STALL_NAME, "档口名称", live == null ? null : live.getStallName(), c.getStallName());
        // 楼层归属**档口**：实时值回查 stall
        if (StringUtils.hasText(c.getFloor())) {
            String liveFloor = live == null || live.getStallId() == null
                    ? null : stallService.getFloorById(live.getStallId());
            addDiff(diffs, FIELD_FLOOR, "楼层", liveFloor, c.getFloor());
        }
        addDiff(diffs, FIELD_IMAGES, "菜品图片", live == null ? null : live.getImages(),
                c.getImages() == null || c.getImages().isEmpty() ? null : c.getImages());
        // 描述属性：按**维度**拆项（键 = attributes.<维度ID>）
        Map<String, String> dimensionNames = dimensionNameById();
        Map<String, Object> liveAttrs = live == null ? null : live.getAttributes();
        JsonMapUtil.parseObject(c.getAttributes()).forEach((dimensionKey, submitted) -> {
            Object liveValue = liveAttrs == null ? null : liveAttrs.get(dimensionKey);
            String label = dimensionNames.getOrDefault(dimensionKey, "描述属性");
            addDiff(diffs, FIELD_ATTR_PREFIX + dimensionKey, label, liveValue, submitted);
        });
        return diffs;
    }

    /** 维度中文名映射（维度 ID 字符串 → name）；字典读失败不阻塞详情（退化为通用文案） */
    private Map<String, String> dimensionNameById() {
        Map<String, String> names = new LinkedHashMap<>();
        for (DishDimensionAdminVO dim : attributeAdminService.listDimensions()) {
            names.put(String.valueOf(dim.getId()), dim.getName());
        }
        return names;
    }

    /** 加一项提交项对照：提交值存在才列（`field` 型只提交改动项）；`same` = 提交值与当前实时值是否一致 */
    private static void addDiff(List<DishCorrectionDifferenceVO> out, String field, String label,
                                Object oldValue, Object newValue) {
        if (newValue == null || (newValue instanceof Collection<?> col && col.isEmpty())) {
            return;   // 用户未提交该项（未改动列留 NULL）⇒ 不入对照表
        }
        String oldText = describe(oldValue);
        String newText = describe(newValue);
        DishCorrectionDifferenceVO diff = new DishCorrectionDifferenceVO();
        diff.setField(field);
        diff.setLabel(label);
        diff.setOldValue(oldText);
        diff.setNewValue(newText);
        diff.setSame(Objects.equals(oldText, newText));
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

    // ==================== 管理端标记已处理（POST /admin/corrections/{id}/handle） ====================

    /**
     * 标记已处理（接受反馈）—— **只归档处置结论 + 投递回执，不改写任何菜品数据**。
     * <p>
     * 菜品资料修改 / {@code gone} 可逆下架由管理员在菜品管理（web/dishes）<b>手动</b>执行，不在本端点。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, DishCorrectionHandleReq req, Long adminId) {
        Correction correction = correctionMapper.selectById(id);
        if (correction == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        // 前置：处置任务仍待处置，否则 400（幂等：重复调用 = 已处置 →「该反馈已处理」）
        requirePending(id, "该反馈已处理");
        String title = req == null ? null : req.getReplyTitle();
        String content = req == null ? null : req.getReplyContent();
        String finalTitle = StringUtils.hasText(title) ? title.trim() : CorrectionConst.HANDLE_TITLE;
        String finalContent = StringUtils.hasText(content) ? content.trim() : CorrectionConst.HANDLE_REPLY;
        // 只归档 outcome=handled（已受理，管理员已手动处理）+ 站内回执；不写 dish / stall
        moderationTaskService.handle(ModerationConst.TYPE_CORRECTION, id, ModerationConst.OUTCOME_HANDLED,
                finalTitle, finalContent, adminId, correction.getUserId());
    }

    /** 处置任务前置判据：任务行缺失 → 4001；已处置 → 指定文案的 400 */
    private void requirePending(Long id, String handledMessage) {
        ModerationTaskVO task = moderationTaskService.findByTypeAndId(ModerationConst.TYPE_CORRECTION, id);
        if (task == null) {
            throw new BusinessException(4001, "处置任务不存在");
        }
        if (!ModerationConst.STATUS_PENDING.equals(task.getStatus())) {
            throw new BusinessException(400, handledMessage);
        }
    }

    // ==================== 管理端驳回（PUT /admin/corrections/{id}） ====================

    @Override
    // 事务口径与 handle 一致：驳回同样「改任务行 + 投递回执」两步，必须原子 ——
    // 否则回执投递失败会留下「任务已归档、作者没收到」的不一致状态。
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, ModerationHandleReq req, Long adminId) {
        Correction correction = correctionMapper.selectById(id);
        if (correction == null) {
            throw new BusinessException(4001, "反馈不存在");
        }
        // 前置：仍待处置，否则 400（幂等：重复调用 = 已处置 →「该反馈已处理」）
        requirePending(id, "该反馈已处理");
        // 处理结论：本端点即驳回动作，outcome 固定 rejected（形态对齐反馈处置，非法值 400）
        String outcome = !StringUtils.hasText(req.getOutcome())
                ? ModerationConst.OUTCOME_REJECTED
                : ParamValidator.requiredInWhitelist(req.getOutcome(), ModerationConst.OUTCOMES, "处理结论");
        if (!ModerationConst.OUTCOME_REJECTED.equals(outcome)) {
            throw new BusinessException(400, "处理结论非法（本端点仅支持 rejected=不采纳/退回）");
        }
        // 处置文案 = 不采纳原因（**必填**，由处置任务服务统一校验；留空 → 400「请填写不采纳原因」）。
        // 标题缺省按类型分派：gone 型语义为「经核实仍在售」。
        boolean gone = CorrectionConst.TYPE_GONE.equals(correction.getType());
        String title = StringUtils.hasText(req.getReplyTitle())
                ? req.getReplyTitle()
                : (gone ? CorrectionConst.GONE_REJECT_TITLE : null);
        moderationTaskService.handle(ModerationConst.TYPE_CORRECTION, id, ModerationConst.OUTCOME_REJECTED,
                title, req.getReplyContent(), adminId, correction.getUserId());
    }
}
