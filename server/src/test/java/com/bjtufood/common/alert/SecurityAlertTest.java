package com.bjtufood.common.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.alert.dto.SecurityAlertVO;
import com.bjtufood.common.alert.entity.SecurityAlert;
import com.bjtufood.common.alert.mapper.SecurityAlertMapper;
import com.bjtufood.common.alert.service.impl.SecurityAlertServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 安全告警记录：类型 / 级别快照、列宽截断、降级口径与面板查询。
 */
class SecurityAlertTest {

    private final SecurityAlertMapper mapper = mock(SecurityAlertMapper.class);

    private SecurityAlertRecorder recorder() {
        return new SecurityAlertRecorder(mapper);
    }

    @Test
    @DisplayName("类型决定级别：同一类型固定同一级别，级别与标签作为快照落库")
    void typeDeterminesSeverity() {
        SecurityAlert row = recorder().buildRow(
                AlertType.LOGIN_LOCKOUT, "管理端登录失败达锁定阈值（疑似暴力破解）", "username=boss", "1.2.3.4");

        assertThat(row.getAlertType()).isEqualTo("LOGIN_LOCKOUT");
        assertThat(row.getAlertTypeLabel()).isEqualTo("登录失败达阈值");
        assertThat(row.getSeverity()).isEqualTo("critical");
        assertThat(row.getSeverityLabel()).isEqualTo("严重");
        assertThat(row.getSourceIp()).isEqualTo("1.2.3.4");

        SecurityAlert info = recorder().buildRow(AlertType.LOGIN_SUCCESS, "管理端登录成功", null, null);
        assertThat(info.getSeverity()).isEqualTo("info");
        assertThat(info.getSourceIp()).isNull();
    }

    @Test
    @DisplayName("超长明细按列宽截断，不整条丢弃（告警的价值在于「发生过什么」）")
    void overlongDetailIsTruncated() {
        String detail = "x".repeat(2_000);

        SecurityAlert row = recorder().buildRow(AlertType.CRAWL_DETECTED, "疑似 ID 枚举", detail, null);

        assertThat(row.getDetail()).hasSize(1024);
        assertThat(row.getTitle()).isEqualTo("疑似 ID 枚举");
    }

    @Test
    @DisplayName("落库失败只吞不抛：告警是旁路，绝不能把业务请求打挂")
    void saveFailureIsSwallowed() {
        when(mapper.insert(any())).thenThrow(new RuntimeException("db down"));

        SecurityAlert row = recorder().buildRow(AlertType.AUDIT_WRITE_FAILURE, "审计写入失败（留痕缺口）", null, null);

        assertThatCode(() -> recorder().save(row)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("面板查询：按类型与级别筛选，按时间倒序且时间格式为 yyyy-MM-dd HH:mm:ss")
    void listAlertsFiltersAndFormats() {
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SecurityAlert> requested = invocation.getArgument(0);
            SecurityAlert row = new SecurityAlert();
            row.setId(7L);
            row.setAlertType("LOGIN_LOCKOUT");
            row.setAlertTypeLabel("登录失败达阈值");
            row.setSeverity("critical");
            row.setSeverityLabel("严重");
            row.setTitle("管理端登录失败达锁定阈值（疑似暴力破解）");
            row.setDetail("username=boss");
            row.setSourceIp(null);
            row.setCreatedAt(LocalDateTime.of(2026, 10, 8, 9, 30, 5));
            requested.setRecords(List.of(row));
            requested.setTotal(1);
            return requested;
        });

        IPage<SecurityAlertVO> result = new SecurityAlertServiceImpl(mapper)
                .listAlerts(1, 20, "LOGIN_LOCKOUT", "critical");

        assertThat(result.getTotal()).isEqualTo(1);
        SecurityAlertVO vo = result.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getSeverityLabel()).isEqualTo("严重");
        assertThat(vo.getCreatedAt()).isEqualTo("2026-10-08 09:30:05");
        // 空 IP 出参为空串（端上无需再判 null）
        assertThat(vo.getSourceIp()).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<SecurityAlert>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), wrapper.capture());
        assertThat(wrapper.getValue().getTargetSql()).contains("alert_type").contains("severity");
    }
}
