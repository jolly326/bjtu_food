package com.bjtufood.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户规模计数（D1 看板用；**跨域聚合由 dashboard.controller 编排**）。
 */
@Data
@Schema(description = "用户规模计数")
public class UserOverviewVO {

    @Schema(description = "用户总数（不含已注销）")
    private Long userCount;

    @Schema(description = "已认证用户数（bind_email 非空）")
    private Long verifiedUserCount;
}
