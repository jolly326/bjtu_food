package com.bjtufood.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 活动 Mapper（最新活动/公众号文章卡片）
 */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
