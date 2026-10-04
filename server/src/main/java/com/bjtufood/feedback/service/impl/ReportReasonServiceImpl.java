package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
import com.bjtufood.common.utils.SortReorderUtil;
import com.bjtufood.feedback.constant.FeedbackConst;
import com.bjtufood.feedback.dto.ReportReasonAdminVO;
import com.bjtufood.feedback.dto.ReportReasonVO;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.entity.ReportReason;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.mapper.ReportReasonMapper;
import com.bjtufood.feedback.service.ReportReasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 举报原因字典服务实现（A7）。
 * <p>
 * 三条不变量见接口 javadoc；`feedbackCount` 用 `feedbackMapper` 统计
 * （`type='report' AND sub=value`，**同域内跨表**，不越界）。
 */
@Service
@RequiredArgsConstructor
public class ReportReasonServiceImpl implements ReportReasonService {

    private static final String STATUS_ON = "on";
    private static final String STATUS_OFF = "off";
    /** 启用条数上限（单选弹层可用性约束，非技术约束） */
    private static final int MAX_ENABLED = 8;
    /** 机器值格式：小写字母 / 数字 / `-` */
    private static final Pattern VALUE_PATTERN = Pattern.compile("^[a-z0-9-]{1,32}$");
    private static final int LABEL_MAX = 32;

    private final ReportReasonMapper reportReasonMapper;
    private final FeedbackMapper feedbackMapper;

    @Override
    public List<ReportReasonVO> listEnabled() {
        return reportReasonMapper.selectList(new LambdaQueryWrapper<ReportReason>()
                        .eq(ReportReason::getStatus, STATUS_ON)
                        .orderByAsc(ReportReason::getOrder))
                .stream()
                .map(r -> new ReportReasonVO(r.getValue(), r.getLabel()))
                .toList();
    }

    @Override
    public List<ReportReasonAdminVO> listAllForAdmin() {
        return reportReasonMapper.selectList(new LambdaQueryWrapper<ReportReason>()
                        .orderByAsc(ReportReason::getOrder)
                        .orderByDesc(ReportReason::getUpdatedAt))
                .stream()
                .map(this::toAdminVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportReasonAdminVO create(String value, String label) {
        String normalizedValue = normalizeValue(value);
        String normalizedLabel = normalizeLabel(label);
        // 机器值全站唯一（uk_reason_value 兜底，应用层先行给友好错误）
        DuplicateGuard.assertUnique(reportReasonMapper, new LambdaQueryWrapper<ReportReason>()
                .eq(ReportReason::getValue, normalizedValue), "机器值已存在");
        // 新增默认启用 ⇒ 启用数将达到 count+1，超上限即拒
        if (countEnabled() >= MAX_ENABLED) {
            throw new BusinessException("启用数已达上限 " + MAX_ENABLED + " 条，请先停用其它原因");
        }
        ReportReason entity = new ReportReason();
        entity.setValue(normalizedValue);
        entity.setLabel(normalizedLabel);
        entity.setStatus(STATUS_ON);
        entity.setOrder(nextOrder());
        reportReasonMapper.insert(entity);
        return toAdminVO(reportReasonMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String label) {
        String normalizedLabel = normalizeLabel(label);
        requireExists(id);
        ReportReason update = new ReportReason();
        update.setId(id);
        update.setLabel(normalizedLabel);
        reportReasonMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!STATUS_ON.equals(status) && !STATUS_OFF.equals(status)) {
            throw new BusinessException("status 非法");
        }
        ReportReason current = reportReasonMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "原因不存在");
        }
        if (STATUS_ON.equals(status)) {
            if (countEnabled() >= MAX_ENABLED) {
                throw new BusinessException("启用数已达上限 " + MAX_ENABLED + " 条");
            }
        } else if (STATUS_ON.equals(current.getStatus()) && countEnabled() <= 1) {
            // 举报是 UGC 治理入口：不接受「把入口配空」
            throw new BusinessException("至少保留 1 条启用中的举报原因");
        }
        if (status.equals(current.getStatus())) {
            return;
        }
        ReportReason update = new ReportReason();
        update.setId(id);
        update.setStatus(status);
        reportReasonMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<SortItem> items) {
        List<Long> existingIds = reportReasonMapper.selectList(null).stream()
                .map(ReportReason::getId).toList();
        Map<Long, Integer> ordered = SortReorderUtil.resolve(items, existingIds);
        for (Map.Entry<Long, Integer> e : ordered.entrySet()) {
            ReportReason update = new ReportReason();
            update.setId(e.getKey());
            update.setOrder(e.getValue());
            reportReasonMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReportReason current = reportReasonMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "原因不存在");
        }
        long used = countFeedbackByValue(current.getValue());
        if (used > 0) {
            // 删掉会让历史举报翻不出中文 ⇒ 下线一律用停用
            throw new BusinessException("该原因已被 " + used + " 条举报引用，不能删除（可改为停用）");
        }
        reportReasonMapper.deleteById(id);
    }

    @Override
    public boolean isSubmittable(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return reportReasonMapper.selectCount(new LambdaQueryWrapper<ReportReason>()
                .eq(ReportReason::getValue, value.trim())
                .eq(ReportReason::getStatus, STATUS_ON)) > 0;
    }

    // ==================== 内部工具 ====================

    private long countEnabled() {
        return reportReasonMapper.selectCount(new LambdaQueryWrapper<ReportReason>()
                .eq(ReportReason::getStatus, STATUS_ON));
    }

    /** 被举报记录引用次数：`type='report' AND sub=value` */
    private long countFeedbackByValue(String value) {
        return feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getType, FeedbackConst.TYPE_REPORT)
                .eq(Feedback::getSub, value));
    }

    /** 新项排最后：现有最大 order + 1 */
    private int nextOrder() {
        List<ReportReason> all = reportReasonMapper.selectList(new LambdaQueryWrapper<ReportReason>()
                .orderByDesc(ReportReason::getOrder));
        return all.isEmpty() || all.get(0).getOrder() == null ? 1 : all.get(0).getOrder() + 1;
    }

    private void requireExists(Long id) {
        if (id == null || reportReasonMapper.selectById(id) == null) {
            throw new BusinessException(4001, "原因不存在");
        }
    }

    private static String normalizeValue(String value) {
        String v = value == null ? null : value.trim();
        if (v == null || !VALUE_PATTERN.matcher(v).matches()) {
            throw new BusinessException("机器值只能包含小写字母、数字与 -，长度 1~32");
        }
        return v;
    }

    private static String normalizeLabel(String label) {
        String l = label == null ? null : label.trim();
        if (!StringUtils.hasText(l)) {
            throw new BusinessException("中文标签不能为空");
        }
        if (l.length() > LABEL_MAX) {
            throw new BusinessException("中文标签不能超过 " + LABEL_MAX + " 字");
        }
        return l;
    }

    private ReportReasonAdminVO toAdminVO(ReportReason entity) {
        ReportReasonAdminVO vo = new ReportReasonAdminVO();
        vo.setId(entity.getId());
        vo.setValue(entity.getValue());
        vo.setLabel(entity.getLabel());
        vo.setOrder(entity.getOrder());
        vo.setStatus(entity.getStatus());
        vo.setFeedbackCount(countFeedbackByValue(entity.getValue()));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
