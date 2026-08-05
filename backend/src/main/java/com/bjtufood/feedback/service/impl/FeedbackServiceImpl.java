package com.bjtufood.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.constant.FeedbackConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.feedback.dto.FeedbackAdminVO;
import com.bjtufood.feedback.dto.FeedbackMyVO;
import com.bjtufood.feedback.dto.FeedbackReq;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户反馈服务实现
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, FeedbackReq req) {
        if (FeedbackConst.TYPE_REPORT.equals(req.getType())) {
            // 举报必须关联被举报动态（复用 user_feedback，不新建举报表）
            if (!FeedbackConst.RELATED_MOMENT.equals(req.getRelatedType()) || req.getRelatedId() == null) {
                throw new BusinessException("举报必须指定关联动态（relatedType=moment 且 relatedId 必填）");
            }
        }
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(req.getType());
        feedback.setContent(req.getContent());
        feedback.setContact(req.getContact());
        feedback.setRelatedType(req.getRelatedType());
        feedback.setRelatedId(req.getRelatedId());
        feedback.setStatus(FeedbackConst.STATUS_PENDING);
        feedbackMapper.insert(feedback);
    }

    @Override
    public List<FeedbackMyVO> listMy(Long userId) {
        return feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getId))
                .stream()
                .map(f -> {
                    FeedbackMyVO vo = new FeedbackMyVO();
                    vo.setId(f.getId());
                    vo.setType(f.getType());
                    vo.setContent(f.getContent());
                    vo.setStatus(f.getStatus());
                    vo.setReply(f.getReply());
                    vo.setCreatedAt(f.getCreatedAt());
                    return vo;
                })
                .toList();
    }

    @Override
    public IPage<FeedbackAdminVO> listForAdmin(String status, String type, Long userId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(StringUtils.hasText(status), Feedback::getStatus, status)
                .eq(StringUtils.hasText(type), Feedback::getType, type)
                .eq(userId != null, Feedback::getUserId, userId)
                .orderByDesc(Feedback::getCreatedAt);

        IPage<Feedback> p = feedbackMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Long> userIds = p.getRecords().stream()
                .map(Feedback::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                    .forEach(u -> userMap.put(u.getId(), u.getNickname()));
        }

        IPage<FeedbackAdminVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(f -> {
            FeedbackAdminVO vo = new FeedbackAdminVO();
            vo.setId(f.getId());
            vo.setUserId(f.getUserId());
            vo.setUserNickname(userMap.get(f.getUserId()));
            vo.setType(f.getType());
            vo.setContent(f.getContent());
            vo.setContact(f.getContact());
            vo.setRelatedType(f.getRelatedType());
            vo.setRelatedId(f.getRelatedId());
            vo.setStatus(f.getStatus());
            vo.setReply(f.getReply());
            vo.setCreatedAt(f.getCreatedAt());
            vo.setHandledAt(f.getHandledAt());
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, Long handlerId, String reply) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BusinessException("反馈不存在");
        }
        feedback.setStatus(FeedbackConst.STATUS_HANDLED);
        feedback.setReply(reply);
        feedback.setHandledAt(LocalDateTime.now());
        feedback.setHandlerId(handlerId);
        feedbackMapper.updateById(feedback);
    }
}
