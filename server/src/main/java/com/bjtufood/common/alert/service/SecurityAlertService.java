package com.bjtufood.common.alert.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.common.alert.dto.SecurityAlertVO;

/**
 * 安全告警记录的只读查询契约（管理端「安全告警」面板）。
 *
 * <p>只有读 —— 告警记录**只追加**，写入由
 * {@link com.bjtufood.common.alert.SecurityAlertNotifier} 在事件发生处完成。
 */
public interface SecurityAlertService {

    /**
     * 分页查询告警记录（按发生时间倒序）。
     *
     * @param page      页码（从 1 开始）
     * @param pageSize  每页条数（上限见 {@code PageUtil}）
     * @param alertType 告警类型键；为空表示全部
     * @param severity  级别键（{@code info} / {@code warn} / {@code critical}）；为空表示全部
     */
    IPage<SecurityAlertVO> listAlerts(int page, int pageSize, String alertType, String severity);
}
