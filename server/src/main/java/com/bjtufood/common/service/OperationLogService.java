package com.bjtufood.common.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.dto.OperationLogVO;

/**
 * 操作日志服务接口（只读查询）
 */
public interface OperationLogService {

    /**
     * 分页查询操作日志（管理端只读）
     *
     * @param keyword 关键词（可选，对 action / targetType 模糊匹配）
     */
    IPage<OperationLogVO> listLogs(Long adminId, String action, String targetType,
                                   String startAt, String endAt, String keyword, int page, int pageSize);
}
