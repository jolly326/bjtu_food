package com.bjtufood.activity.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.activity.dto.ActivityVO;
import com.bjtufood.activity.entity.Activity;

import java.util.List;

/**
 * 活动服务（最新活动/公众号文章卡片）
 */
public interface ActivityService {

    /**
     * 用户端：展示中活动列表（status=enabled，按 sort_order 升序、created_at 降序）。
     */
    List<ActivityVO> listEnabled();

    /**
     * 管理端：分页查询（全部状态，可按关键词/状态过滤）。
     */
    IPage<Activity> listForAdmin(String keyword, String status, int page, int pageSize);

    Activity create(Activity activity);

    void update(Long id, Activity activity);

    void delete(Long id);
}
