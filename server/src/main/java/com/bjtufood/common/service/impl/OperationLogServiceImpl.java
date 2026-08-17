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
                                          String startAt, String endAt, String keyword, int page, int pageSize) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];

        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(adminId != null, OperationLog::getAdminId, adminId)
                .eq(StringUtils.hasText(action), OperationLog::getAction, action)
                .eq(StringUtils.hasText(targetType), OperationLog::getTargetType, targetType)
                .ge(StringUtils.hasText(startAt), OperationLog::getCreatedAt, startAt)
                .le(StringUtils.hasText(endAt), OperationLog::getCreatedAt, endAt);

        // 关键词模糊匹配动作标识或操作对象类型；用 and(...) 包一层括号，避免 OR 打散上面的等值/范围条件。
        // 必须在 orderByDesc 之前追加，否则条件片段会拼到 ORDER BY 之后生成非法 SQL。
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OperationLog::getAction, kw).or().like(OperationLog::getTargetType, kw));
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);

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
