package com.bjtufood.common.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.dto.OperationLogVO;

/**
 * 操作日志服务接口（只读查询）
 */
public interface OperationLogService {

    /**
     * 分页查询操作日志（管理端只读）
     */
    IPage<OperationLogVO> listLogs(Long adminId, String action, String targetType,
                                   String startAt, String endAt, int page, int pageSize);
}
