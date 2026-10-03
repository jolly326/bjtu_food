package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A7 新增举报原因请求。
 *
 * <p>`value` **机器值由管理员填写、在用后不可改**（历史举报按它落库）；
 * 故**没有**「改机器值」的端点 —— 要改就停用旧值、新建一个。
 */
@Data
@Schema(description = "举报原因新增请求")
public class ReportReasonSaveReq {

    @Schema(description = "机器值（小写字母 / 数字 / -，1~32；全站唯一；在用后不可改）", example = "spam")
    @NotBlank(message = "机器值不能为空")
    @Size(max = 32, message = "机器值不能超过 32 字符")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "机器值只能包含小写字母、数字与 -")
    private String value;

    @Schema(description = "中文标签（1~32 字）", example = "垃圾广告 / 营销刷屏")
    @NotBlank(message = "中文标签不能为空")
    @Size(max = 32, message = "中文标签不能超过 32 字")
    private String label;
}
