package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.DuplicateGuard;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报原因字典服务实现（A7）。
 * <p>
 * 数据锚在原因 ID；`feedbackCount` 用 `feedbackMapper` 统计
 * （`type='report' AND sub_reason_id = id`，**同域内跨表**，不越界）。
 */
@Service
@RequiredArgsConstructor
public class ReportReasonServiceImpl implements ReportReasonService {

    private static final String STATUS_ON = "on";
    private static final String STATUS_OFF = "off";
    /** 启用条数上限（单选弹层可用性约束，非技术约束） */
    private static final int MAX_ENABLED = 8;
    private static final int NAME_MAX = 32;

    private final ReportReasonMapper reportReasonMapper;
    private final FeedbackMapper feedbackMapper;

    @Override
    public List<ReportReasonVO> listEnabled() {
        return reportReasonMapper.selectList(new LambdaQueryWrapper<ReportReason>()
                        .eq(ReportReason::getStatus, STATUS_ON)
                        .orderByAsc(ReportReason::getId))
                .stream()
                .map(r -> new ReportReasonVO(r.getId(), r.getName()))
                .toList();
    }

    @Override
    public List<ReportReasonAdminVO> listAllForAdmin() {
        return reportReasonMapper.selectList(new LambdaQueryWrapper<ReportReason>()
                        .orderByAsc(ReportReason::getId))
                .stream()
                .map(this::toAdminVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportReasonAdminVO create(String name) {
        String normalizedName = normalizeName(name);
        // 名唯一由应用层保证（name 无唯一索引，与 dish_attribute_value 同口径）
        DuplicateGuard.assertUnique(reportReasonMapper, new LambdaQueryWrapper<ReportReason>()
                .eq(ReportReason::getName, normalizedName), "中文名已存在");
        // 新增默认启用 ⇒ 启用数将达到 count+1，超上限即拒
        if (countEnabled() >= MAX_ENABLED) {
            throw new BusinessException("启用数已达上限 " + MAX_ENABLED + " 条，请先停用其它原因");
        }
        ReportReason entity = new ReportReason();
        entity.setName(normalizedName);
        entity.setStatus(STATUS_ON);
        reportReasonMapper.insert(entity);
        return toAdminVO(reportReasonMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String name) {
        String normalizedName = normalizeName(name);
        requireExists(id);
        ReportReason update = new ReportReason();
        update.setId(id);
        update.setName(normalizedName);
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
    public void delete(Long id) {
        ReportReason current = reportReasonMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(4001, "原因不存在");
        }
        long used = countFeedbackByReasonId(current.getId());
        if (used > 0) {
            // 删掉会让历史举报翻不出中文 ⇒ 下线一律用停用
            throw new BusinessException("该原因已被 " + used + " 条举报引用，不能删除（可改为停用）");
        }
        reportReasonMapper.deleteById(id);
    }

    @Override
    public boolean isSubmittable(Long reasonId) {
        if (reasonId == null) {
            return false;
        }
        return reportReasonMapper.selectCount(new LambdaQueryWrapper<ReportReason>()
                .eq(ReportReason::getId, reasonId)
                .eq(ReportReason::getStatus, STATUS_ON)) > 0;
    }

    @Override
    public Map<Long, String> nameByIdAll() {
        Map<Long, String> names = new HashMap<>();
        for (ReportReason reason : reportReasonMapper.selectList(null)) {
            names.put(reason.getId(), reason.getName());
        }
        return names;
    }

    // ==================== 内部工具 ====================

    private long countEnabled() {
        return reportReasonMapper.selectCount(new LambdaQueryWrapper<ReportReason>()
                .eq(ReportReason::getStatus, STATUS_ON));
    }

    /** 被举报记录引用次数：`type='report' AND sub_reason_id = id` */
    private long countFeedbackByReasonId(Long reasonId) {
        return feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getType, FeedbackConst.TYPE_REPORT)
                .eq(Feedback::getSubReasonId, reasonId));
    }

    private void requireExists(Long id) {
        if (id == null || reportReasonMapper.selectById(id) == null) {
            throw new BusinessException(4001, "原因不存在");
        }
    }

    private static String normalizeName(String name) {
        String n = name == null ? null : name.trim();
        if (n == null || n.isEmpty()) {
            throw new BusinessException("中文名不能为空");
        }
        if (n.length() > NAME_MAX) {
            throw new BusinessException("中文名不能超过 " + NAME_MAX + " 字");
        }
        return n;
    }

    private ReportReasonAdminVO toAdminVO(ReportReason entity) {
        ReportReasonAdminVO vo = new ReportReasonAdminVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setStatus(entity.getStatus());
        vo.setFeedbackCount(countFeedbackByReasonId(entity.getId()));
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
