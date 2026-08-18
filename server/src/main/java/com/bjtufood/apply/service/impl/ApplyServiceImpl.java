package com.bjtufood.apply.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.apply.constant.ApplyConst;
import com.bjtufood.apply.dto.ApplyHandleReq;
import com.bjtufood.apply.dto.ApplyReq;
import com.bjtufood.apply.dto.ApplyVO;
import com.bjtufood.apply.dto.SubmissionVO;
import com.bjtufood.apply.entity.ApplyAction;
import com.bjtufood.apply.mapper.ApplyActionMapper;
import com.bjtufood.apply.service.ApplyService;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DateTimeUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.moment.dto.MomentVO;
import com.bjtufood.moment.service.MomentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体贡献统一申请服务实现（task-12.1）
 */
@Service
@RequiredArgsConstructor
public class ApplyServiceImpl implements ApplyService {

    private final ApplyActionMapper applyActionMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final MomentService momentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long applicantId, ApplyReq req) {
        String entityType = req.getEntityType() == null ? "" : req.getEntityType().toUpperCase();
        String applyType = req.getApplyType() == null ? "" : req.getApplyType().toUpperCase();

        validateApplyType(entityType, applyType, req.getEntityId());

        // 同 (entityType, entityId, applyType) 仅一条 pending —— 重复提交拦截 409
        if (req.getEntityId() != null) {
            long pending = applyActionMapper.selectCount(new LambdaQueryWrapper<ApplyAction>()
                    .eq(ApplyAction::getEntityType, entityType)
                    .eq(ApplyAction::getEntityId, req.getEntityId())
                    .eq(ApplyAction::getApplyType, applyType)
                    .eq(ApplyAction::getStatus, ApplyConst.STATUS_PENDING));
            if (pending > 0) {
                throw new BusinessException(409, "已有待审申请，请勿重复提交");
            }
        }

        ApplyAction apply = new ApplyAction();
        apply.setApplicantId(applicantId);
        apply.setEntityType(entityType);
        apply.setEntityId(req.getEntityId());
        apply.setApplyType(applyType);
        apply.setPayload(payloadToJson(req.getPayload()));
        apply.setStatus(ApplyConst.STATUS_PENDING);
        try {
            applyActionMapper.insert(apply);
        } catch (DuplicateKeyException e) {
            // 并发下前置 selectCount 通过但插入瞬间已被他人抢先落库，唯一键兜底：
            // 统一转为业务提示，避免 500
            throw new BusinessException("您已报名");
        }
        return apply.getId();
    }

    @Override
    public List<ApplyVO> myApplies(Long applicantId, String status) {
        LambdaQueryWrapper<ApplyAction> w = new LambdaQueryWrapper<ApplyAction>()
                .eq(ApplyAction::getApplicantId, applicantId)
                .orderByDesc(ApplyAction::getCreatedAt);
        if (StringUtils.hasText(status)) {
            w.eq(ApplyAction::getStatus, status);
        }
        return applyActionMapper.selectList(w).stream().map(this::toVO).toList();
    }

    @Override
    public List<SubmissionVO> mySubmissions(Long applicantId) {
        List<SubmissionVO> result = new ArrayList<>();

        // 实体贡献申请标签
        List<ApplyAction> applies = applyActionMapper.selectList(new LambdaQueryWrapper<ApplyAction>()
                .eq(ApplyAction::getApplicantId, applicantId)
                .orderByDesc(ApplyAction::getCreatedAt));
        for (ApplyAction a : applies) {
            SubmissionVO vo = new SubmissionVO();
            vo.setType("apply");
            vo.setId(a.getId());
            vo.setEntityType(a.getEntityType());
            vo.setAction(a.getApplyType());
            vo.setTitle(previewTitle(a));
            vo.setStatus(a.getStatus());
            vo.setCreatedAt(a.getCreatedAt());
            result.add(vo);
        }

        // 动态标签
        List<MomentVO> moments = momentService.myMoments(applicantId, null);
        for (MomentVO m : moments) {
            SubmissionVO vo = new SubmissionVO();
            vo.setType("moment");
            vo.setId(m.getId());
            vo.setEntityType(null);
            vo.setAction(null);
            String content = m.getContent();
            vo.setTitle(StringUtils.hasText(content)
                    ? (content.length() > 30 ? content.substring(0, 30) + "…" : content)
                    : "动态");
            vo.setStatus(m.getAuditStatus());
            vo.setCreatedAt(m.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public IPage<ApplyVO> adminList(String status, String entityType, String applyType, int page, int pageSize) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        LambdaQueryWrapper<ApplyAction> w = new LambdaQueryWrapper<ApplyAction>()
                .orderByDesc(ApplyAction::getCreatedAt);
        if (StringUtils.hasText(status)) w.eq(ApplyAction::getStatus, status);
        if (StringUtils.hasText(entityType)) w.eq(ApplyAction::getEntityType, entityType.toUpperCase());
        if (StringUtils.hasText(applyType)) w.eq(ApplyAction::getApplyType, applyType.toUpperCase());
        IPage<ApplyAction> p = applyActionMapper.selectPage(new Page<>(page, pageSize), w);
        IPage<ApplyVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long adminId) {
        ApplyAction apply = applyActionMapper.selectById(id);
        if (apply == null) throw new BusinessException("申请不存在");
        // 自审拦截：申请人不能审核自己的申请
        if (apply.getApplicantId() != null && apply.getApplicantId().equals(adminId)) {
            throw new BusinessException(400, "不能审核自己提交的申请");
        }
        // 状态机闭合 + 乐观锁：仅当仍为 PENDING 才允许更新，影响行数=0 表示已被并发处理
        int rows = applyActionMapper.update(null, new LambdaUpdateWrapper<ApplyAction>()
                .eq(ApplyAction::getId, id)
                .eq(ApplyAction::getStatus, ApplyConst.STATUS_PENDING)
                .set(ApplyAction::getStatus, ApplyConst.STATUS_APPROVED)
                .set(ApplyAction::getHandledBy, adminId)
                .set(ApplyAction::getHandledAt, DateTimeUtil.now())
                .set(ApplyAction::getRejectReason, (String) null));
        if (rows == 0) {
            throw new BusinessException("该申请已处理");
        }

        // 触发副作用
        applySideEffect(apply);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long adminId, ApplyHandleReq req) {
        if (!StringUtils.hasText(req.getRejectReason())) {
            throw new BusinessException(400, "退回原因不能为空");
        }
        ApplyAction apply = applyActionMapper.selectById(id);
        if (apply == null) throw new BusinessException("申请不存在");
        // 自审拦截：申请人不能审核自己的申请
        if (apply.getApplicantId() != null && apply.getApplicantId().equals(adminId)) {
            throw new BusinessException(400, "不能审核自己提交的申请");
        }
        // 状态机闭合 + 乐观锁：仅当仍为 PENDING 才允许更新，影响行数=0 表示已被并发处理
        int rows = applyActionMapper.update(null, new LambdaUpdateWrapper<ApplyAction>()
                .eq(ApplyAction::getId, id)
                .eq(ApplyAction::getStatus, ApplyConst.STATUS_PENDING)
                .set(ApplyAction::getStatus, ApplyConst.STATUS_REJECTED)
                .set(ApplyAction::getRejectReason, req.getRejectReason())
                .set(ApplyAction::getHandledBy, adminId)
                .set(ApplyAction::getHandledAt, DateTimeUtil.now()));
        if (rows == 0) {
            throw new BusinessException("该申请已处理");
        }
    }

    // ==================== 内部辅助 ====================

    private void validateApplyType(String entityType, String applyType, Long entityId) {
        boolean validEntity = entityType.equals(ApplyConst.ENTITY_DISH)
                || entityType.equals(ApplyConst.ENTITY_STALL)
                || entityType.equals(ApplyConst.ENTITY_CANTEEN);
        if (!validEntity) throw new BusinessException(400, "非法的实体类型：" + entityType);

        boolean validType = applyType.equals(ApplyConst.TYPE_NEW)
                || applyType.equals(ApplyConst.TYPE_CLOSE)
                || applyType.equals(ApplyConst.TYPE_CHANGE);
        if (!validType) throw new BusinessException(400, "非法的申请类型：" + applyType);

        // 下架/变更类必须关联实体；新增类可空
        if (!ApplyConst.TYPE_NEW.equals(applyType) && entityId == null) {
            throw new BusinessException(400, "下架/变更类申请必须指定 entityId");
        }
    }

    /**
     * 审核通过副作用：新增写实体 / 下架置 off|closed / 变更写回字段。
     */
    private void applySideEffect(ApplyAction apply) {
        JsonNode payload = parsePayload(apply.getPayload());
        switch (apply.getEntityType()) {
            case ApplyConst.ENTITY_DISH -> handleDish(apply, payload);
            case ApplyConst.ENTITY_STALL -> handleStall(apply, payload);
            case ApplyConst.ENTITY_CANTEEN -> handleCanteen(apply, payload);
            default -> throw new BusinessException(400, "非法的实体类型：" + apply.getEntityType());
        }
    }

    private void handleDish(ApplyAction apply, JsonNode payload) {
        if (ApplyConst.TYPE_NEW.equals(apply.getApplyType())) {
            Dish dish = new Dish();
            dish.setStallId(getLong(payload, "stallId"));
            if (dish.getStallId() == null || stallMapper.selectById(dish.getStallId()) == null) {
                throw new BusinessException(400, "新增菜品必须指定有效的 stallId");
            }
            dish.setName(getText(payload, "name"));
            dish.setPrice(getInt(payload, "price"));
            dish.setOriginalPrice(getInt(payload, "originalPrice"));
            dish.setPromoPrice(getInt(payload, "promoPrice"));
            dish.setImages(JsonListUtil.toJson(getTextList(payload, "images")));
            dish.setTags(getText(payload, "tags"));
            dish.setDescription(getText(payload, "description"));
            dish.setSpiceLevel(getInt(payload, "spiceLevel"));
            dish.setPortion(getInt(payload, "portion"));
            dish.setServePeriod(getText(payload, "servePeriod"));
            dish.setLimited(getInt(payload, "limited"));
            dish.setStatus(com.bjtufood.dish.constant.DishConst.STATUS_ON);
            dish.setAuditStatus(com.bjtufood.dish.constant.DishConst.AUDIT_PENDING);
            dish.setRejectReason(null);
            dish.setCreatedBy(apply.getApplicantId());
            dish.setAvgRating(java.math.BigDecimal.ZERO);
            dish.setRatingCount(0);
            dish.setViewCount(0);
            dishMapper.insert(dish);
            apply.setEntityId(dish.getId());
            applyActionMapper.updateById(apply);
        } else if (ApplyConst.TYPE_CLOSE.equals(apply.getApplyType())) {
            Dish dish = requireDish(apply.getEntityId());
            dish.setStatus("off");
            dishMapper.updateById(dish);
        } else { // CHANGE
            Dish dish = requireDish(apply.getEntityId());
            writeBackDish(dish, payload);
            dishMapper.updateById(dish);
        }
    }

    private void writeBackDish(Dish dish, JsonNode payload) {
        if (payload == null) return;
        if (payload.has("name")) dish.setName(getText(payload, "name"));
        if (payload.has("price")) dish.setPrice(getInt(payload, "price"));
        if (payload.has("originalPrice")) dish.setOriginalPrice(getInt(payload, "originalPrice"));
        if (payload.has("promoPrice")) dish.setPromoPrice(getInt(payload, "promoPrice"));
        if (payload.has("images")) dish.setImages(JsonListUtil.toJson(getTextList(payload, "images")));
        if (payload.has("tags")) dish.setTags(getText(payload, "tags"));
        if (payload.has("description")) dish.setDescription(getText(payload, "description"));
        if (payload.has("stallId")) dish.setStallId(getLong(payload, "stallId"));
    }

    private void handleStall(ApplyAction apply, JsonNode payload) {
        if (ApplyConst.TYPE_NEW.equals(apply.getApplyType())) {
            Stall stall = new Stall();
            stall.setCanteenId(getLong(payload, "canteenId"));
            if (stall.getCanteenId() == null || canteenMapper.selectById(stall.getCanteenId()) == null) {
                throw new BusinessException(400, "新增档口必须指定有效的 canteenId");
            }
            stall.setName(getText(payload, "name"));
            stall.setFloor(getText(payload, "floor"));
            stall.setWindowNo(getText(payload, "windowNo"));
            stall.setBusinessHours(getText(payload, "businessHours"));
            stall.setLocation(getText(payload, "location"));
            stall.setDescription(getText(payload, "description"));
            stall.setStatus("open");
            stall.setAuditStatus("pending");
            stall.setRejectReason(null);
            stallMapper.insert(stall);
            apply.setEntityId(stall.getId());
            applyActionMapper.updateById(apply);
        } else if (ApplyConst.TYPE_CLOSE.equals(apply.getApplyType())) {
            Stall stall = requireStall(apply.getEntityId());
            stall.setStatus("closed");
            stallMapper.updateById(stall);
        } else {
            Stall stall = requireStall(apply.getEntityId());
            if (payload != null) {
                if (payload.has("name")) stall.setName(getText(payload, "name"));
                if (payload.has("floor")) stall.setFloor(getText(payload, "floor"));
                if (payload.has("windowNo")) stall.setWindowNo(getText(payload, "windowNo"));
                if (payload.has("businessHours")) stall.setBusinessHours(getText(payload, "businessHours"));
                if (payload.has("location")) stall.setLocation(getText(payload, "location"));
                if (payload.has("description")) stall.setDescription(getText(payload, "description"));
            }
            stallMapper.updateById(stall);
        }
    }

    private void handleCanteen(ApplyAction apply, JsonNode payload) {
        if (ApplyConst.TYPE_NEW.equals(apply.getApplyType())) {
            Canteen canteen = new Canteen();
            canteen.setName(getText(payload, "name"));
            canteen.setLocation(getText(payload, "location"));
            canteen.setDescription(getText(payload, "description"));
            canteen.setStatus("open");
            canteen.setAuditStatus("pending");
            canteen.setRejectReason(null);
            canteenMapper.insert(canteen);
            apply.setEntityId(canteen.getId());
            applyActionMapper.updateById(apply);
        } else if (ApplyConst.TYPE_CLOSE.equals(apply.getApplyType())) {
            Canteen canteen = requireCanteen(apply.getEntityId());
            canteen.setStatus("closed");
            canteenMapper.updateById(canteen);
        } else {
            Canteen canteen = requireCanteen(apply.getEntityId());
            if (payload != null) {
                if (payload.has("name")) canteen.setName(getText(payload, "name"));
                if (payload.has("location")) canteen.setLocation(getText(payload, "location"));
                if (payload.has("description")) canteen.setDescription(getText(payload, "description"));
            }
            canteenMapper.updateById(canteen);
        }
    }

    private Dish requireDish(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) throw new BusinessException("菜品不存在");
        return dish;
    }

    private Stall requireStall(Long id) {
        Stall stall = stallMapper.selectById(id);
        if (stall == null) throw new BusinessException("档口不存在");
        return stall;
    }

    private Canteen requireCanteen(Long id) {
        Canteen canteen = canteenMapper.selectById(id);
        if (canteen == null) throw new BusinessException("食堂不存在");
        return canteen;
    }

    private JsonNode parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 申请快照统一序列化为 JSON 字符串落库：
     * - null → null
     * - String → 原样（老契约：前端传 JSON 字符串）
     * - 其他（Map/List/对象）→ objectMapper 序列化（新契约：小程序直传对象）
     */
    private String payloadToJson(Object payload) {
        if (payload == null) return null;
        if (payload instanceof String s) return StringUtils.hasText(s) ? s : null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private List<String> getTextList(JsonNode node, String field) {
        List<String> list = new ArrayList<>();
        if (node.has(field) && node.get(field).isArray()) {
            node.get(field).forEach(n -> list.add(n.asText()));
        }
        return list;
    }

    private Integer getInt(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }

    private Long getLong(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }

    private String previewTitle(ApplyAction apply) {
        JsonNode payload = parsePayload(apply.getPayload());
        String name = getText(payload, "name");
        String prefix = switch (apply.getApplyType()) {
            case ApplyConst.TYPE_NEW -> "新增";
            case ApplyConst.TYPE_CLOSE -> "下架";
            case ApplyConst.TYPE_CHANGE -> "变更";
            default -> "";
        };
        String entityLabel = switch (apply.getEntityType()) {
            case ApplyConst.ENTITY_DISH -> "菜品";
            case ApplyConst.ENTITY_STALL -> "档口";
            case ApplyConst.ENTITY_CANTEEN -> "食堂";
            default -> "";
        };
        if (StringUtils.hasText(name)) {
            return prefix + entityLabel + "：" + name;
        }
        return prefix + entityLabel;
    }

    private ApplyVO toVO(ApplyAction a) {
        ApplyVO vo = new ApplyVO();
        vo.setId(a.getId());
        vo.setApplicantId(a.getApplicantId());
        vo.setEntityType(a.getEntityType());
        vo.setEntityId(a.getEntityId());
        vo.setApplyType(a.getApplyType());
        vo.setStatus(a.getStatus());
        vo.setPayload(a.getPayload());
        vo.setRejectReason(a.getRejectReason());
        vo.setHandledBy(a.getHandledBy());
        vo.setHandledAt(a.getHandledAt());
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }
}
