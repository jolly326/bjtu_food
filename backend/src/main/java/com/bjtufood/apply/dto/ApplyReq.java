package com.bjtufood.apply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交实体贡献申请请求（task-12.1，STU 统一入口 POST /my/apply）
 */
@Data
@Schema(description = "提交实体贡献申请")
public class ApplyReq {

    /** 实体类型：DISH / STALL / CANTEEN */
    @Schema(description = "实体类型：DISH/STALL/CANTEEN", example = "DISH")
    @NotBlank(message = "实体类型不能为空")
    private String entityType;

    /** 关联实体ID（下架/变更类必填；新增类可空） */
    @Schema(description = "关联实体ID（下架/变更类必填；新增类可空）", example = "1")
    private Long entityId;

    /** 申请类型：NEW / CLOSE / CHANGE */
    @Schema(description = "申请类型：NEW/CLOSE/CHANGE", example = "CLOSE")
    @NotBlank(message = "申请类型不能为空")
    private String applyType;

    /**
     * 申请字段快照（承载新增/变更字段）。
     * 兼容两种入参：JSON 字符串（老契约）或对象（小程序前端 submitApply 直传 Record）。
     * Service 层统一序列化为 JSON 字符串落库（apply_action.payload 为 TEXT）。
     */
    @Schema(description = "申请字段快照（JSON 对象或 JSON 字符串）")
    private Object payload;
}
