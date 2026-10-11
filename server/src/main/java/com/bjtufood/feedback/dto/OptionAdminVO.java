package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类字典出参（管理端，`feedback_option` / `report_option` 共用形状）。
 * <p>
 * 比公开 VO（{@link OptionVO}）多 3 项：`status` / `usageCount` / `updatedAt`
 * —— 支撑列表展示与「删除前判断」。
 */
@Data
@Schema(description = "管理端分类字典出参")
public class OptionAdminVO {

    @Schema(description = "选项ID（落库引用列 = 工单表的 option_id）")
    private Long id;

    @Schema(description = "中文名", example = "程序功能Bug")
    private String name;

    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    @Schema(description = "被工单引用次数（本字典的消费表按 option_id 统计）—— 删除前判断")
    private Long usageCount;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
