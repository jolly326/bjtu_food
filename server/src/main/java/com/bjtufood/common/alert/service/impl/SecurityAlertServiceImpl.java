package com.bjtufood.common.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.alert.dto.SecurityAlertVO;
import com.bjtufood.common.alert.entity.SecurityAlert;
import com.bjtufood.common.alert.mapper.SecurityAlertMapper;
import com.bjtufood.common.alert.service.SecurityAlertService;
import com.bjtufood.common.utils.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

/**
 * 安全告警记录查询实现。
 *
 * <p>排序按 {@code created_at DESC, id DESC}：同一秒内产生的多条告警需要稳定的相对顺序，
 * 仅按时间排序会让页面刷新时行序抖动。
 */
@Service
@RequiredArgsConstructor
public class SecurityAlertServiceImpl implements SecurityAlertService {

    /** 出参时间格式（与全站管理端一致） */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecurityAlertMapper securityAlertMapper;

    @Override
    public IPage<SecurityAlertVO> listAlerts(int page, int pageSize, String alertType, String severity) {
        int[] normalized = PageUtil.normalize(page, pageSize);
        LambdaQueryWrapper<SecurityAlert> wrapper = new LambdaQueryWrapper<SecurityAlert>()
                .eq(StringUtils.hasText(alertType), SecurityAlert::getAlertType, alertType)
                .eq(StringUtils.hasText(severity), SecurityAlert::getSeverity, severity)
                .orderByDesc(SecurityAlert::getCreatedAt)
                .orderByDesc(SecurityAlert::getId);
        return securityAlertMapper
                .selectPage(new Page<>(normalized[0], normalized[1]), wrapper)
                .convert(this::toVO);
    }

    private SecurityAlertVO toVO(SecurityAlert row) {
        SecurityAlertVO vo = new SecurityAlertVO();
        vo.setId(row.getId());
        vo.setAlertType(row.getAlertType());
        vo.setAlertTypeLabel(row.getAlertTypeLabel());
        vo.setSeverity(row.getSeverity());
        vo.setSeverityLabel(row.getSeverityLabel());
        vo.setTitle(row.getTitle());
        vo.setDetail(row.getDetail());
        vo.setSourceIp(row.getSourceIp() == null ? "" : row.getSourceIp());
        vo.setCreatedAt(row.getCreatedAt() == null ? "" : row.getCreatedAt().format(TIME_FORMAT));
        return vo;
    }
}
