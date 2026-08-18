package com.bjtufood.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.activity.dto.ActivityVO;
import com.bjtufood.activity.entity.Activity;
import com.bjtufood.activity.mapper.ActivityMapper;
import com.bjtufood.activity.service.ActivityService;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 活动服务实现（最新活动/公众号文章卡片）
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;

    @Override
    public List<ActivityVO> listEnabled() {
        return activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, "enabled")
                        .orderByAsc(Activity::getSortOrder)
                        .orderByDesc(Activity::getCreatedAt))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public IPage<Activity> listForAdmin(String keyword, String status, int page, int pageSize) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<Activity>()
                .like(StringUtils.hasText(keyword), Activity::getTitle, keyword)
                .or(StringUtils.hasText(keyword), w -> w.like(Activity::getDescription, keyword))
                .eq(StringUtils.hasText(status), Activity::getStatus, status)
                .orderByAsc(Activity::getSortOrder)
                .orderByDesc(Activity::getCreatedAt);
        return activityMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public Activity create(Activity activity) {
        if (activity == null || !StringUtils.hasText(activity.getTitle())) {
            throw new BusinessException("活动标题不能为空");
        }
        activity.setId(null);
        activity.setStatus(StringUtils.hasText(activity.getStatus()) ? activity.getStatus() : "enabled");
        activity.setSortOrder(activity.getSortOrder() == null ? 0 : activity.getSortOrder());
        activityMapper.insert(activity);
        return activity;
    }

    @Override
    public void update(Long id, Activity activity) {
        if (activity == null || !StringUtils.hasText(activity.getTitle())) {
            throw new BusinessException("活动标题不能为空");
        }
        activity.setId(id);
        activityMapper.updateById(activity);
    }

    @Override
    public void delete(Long id) {
        activityMapper.deleteById(id);
    }

    private ActivityVO toVO(Activity a) {
        ActivityVO vo = new ActivityVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setDescription(a.getDescription());
        vo.setImage(a.getImage());
        vo.setArticleUrl(a.getArticleUrl());
        vo.setPublishTime(a.getCreatedAt());
        return vo;
    }
}
