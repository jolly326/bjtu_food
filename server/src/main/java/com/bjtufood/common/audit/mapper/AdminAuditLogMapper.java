package com.bjtufood.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.common.audit.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端操作审计日志 Mapper。
 * <p>
 * 只提供写入与查询 —— 审计表**只追加**，无更新 / 删除路径。
 */
@Mapper
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
