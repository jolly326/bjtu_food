package com.bjtufood.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.dto.OperationLogVO;
import com.bjtufood.common.entity.OperationLog;
import com.bjtufood.common.mapper.OperationLogMapper;
import com.bjtufood.common.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志服务实现（只读查询）
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    @Override
    public IPage<OperationLogVO> listLogs(Long adminId, String action, String targetType,
                                          String startAt, String endAt, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;

        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(adminId != null, OperationLog::getAdminId, adminId)
                .eq(StringUtils.hasText(action), OperationLog::getAction, action)
                .eq(StringUtils.hasText(targetType), OperationLog::getTargetType, targetType)
                .ge(StringUtils.hasText(startAt), OperationLog::getCreatedAt, startAt)
                .le(StringUtils.hasText(endAt), OperationLog::getCreatedAt, endAt)
                .orderByDesc(OperationLog::getCreatedAt);

        IPage<OperationLog> p = operationLogMapper.selectPage(new Page<>(page, pageSize), wrapper);

        List<Long> adminIds = p.getRecords().stream()
                .map(OperationLog::getAdminId)
                .filter(id -> id != null && id != 0)
                .distinct()
                .toList();
        Map<Long, String> adminMap = new HashMap<>();
        if (!adminIds.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, adminIds))
                    .forEach(u -> adminMap.put(u.getId(), u.getNickname()));
        }

        IPage<OperationLogVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(log -> {
            OperationLogVO vo = new OperationLogVO();
            vo.setId(log.getId());
            vo.setAdminId(log.getAdminId());
            vo.setAdminNickname(adminMap.get(log.getAdminId()));
            vo.setAction(log.getAction());
            vo.setTargetType(log.getTargetType());
            vo.setTargetId(log.getTargetId());
            vo.setIp(log.getIp());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList());
        return result;
    }
}
