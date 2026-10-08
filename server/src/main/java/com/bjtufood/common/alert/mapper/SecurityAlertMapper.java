package com.bjtufood.common.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.common.alert.entity.SecurityAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全告警记录 Mapper。
 * <p>
 * 只提供写入与查询 —— 告警记录**只追加**，无更新 / 删除路径。
 */
@Mapper
public interface SecurityAlertMapper extends BaseMapper<SecurityAlert> {
}
