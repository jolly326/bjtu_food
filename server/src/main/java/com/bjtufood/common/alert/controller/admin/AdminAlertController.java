package com.bjtufood.common.alert.controller.admin;

import com.bjtufood.common.alert.dto.SecurityAlertVO;
import com.bjtufood.common.alert.service.SecurityAlertService;
import com.bjtufood.common.result.AdminPageResult;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端「安全告警」面板的只读端点。
 *
 * <p><b>为什么是只读</b>：告警记录只追加 —— 没有修改 / 删除入口，
 * 面板也不提供任何写操作（能删告警的人就能抹掉自己的痕迹）。
 *
 * <p>鉴权：全部 `/admin/**` 需管理端 token；本端点只有 GET，故 `viewer` 角色同样可查
 * （只读角色的用途正是「看，但不动手」）。
 */
@Tag(name = "16. 后台安全告警", description = "管理员按类型 / 级别回看安全告警记录。需要管理员 token。")
@RestController
@RequestMapping("/admin/alerts")
@RequiredArgsConstructor
@SecurityRequirement(name = "adminToken")
public class AdminAlertController {

    private final SecurityAlertService securityAlertService;

    @Operation(summary = "安全告警记录", description = "用途：分页回看安全告警（登录成功 / 登录失败达阈值 / "
            + "动态口令启停 / 口令修改 / 审计写入失败 / 违规累积处置 / 爬取检测），"
            + "支持按类型与级别筛选。记录只追加，无修改与删除入口。"
            + "测试示例：/admin/alerts?page=1&pageSize=20&severity=critical")
    @GetMapping
    public Result<AdminPageResult<SecurityAlertVO>> listAlerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "告警类型键（如 LOGIN_LOCKOUT）；不传 = 全部")
            @RequestParam(required = false) String alertType,
            @Parameter(description = "级别键：info / warn / critical；不传 = 全部")
            @RequestParam(required = false) String severity) {
        return Result.success(AdminPageResult.of(
                securityAlertService.listAlerts(page, pageSize, alertType, severity)));
    }
}
