package com.bjtufood.moderation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处置任务只读投影（**跨域契约**：三类工单的管理端列表按 {@code type_id} 批量回填处置结果）。
 * <p>
 * 工单属主域（feedback / correction）不触碰本域实体，只消费本投影 ——
 * 这也是「处置文案真源在任务表、管理端回显读任务表」的落地形态。
 */
@Data
@Schema(description = "处置任务只读投影")
public class ModerationTaskVO {

    @Schema(description = "处置状态：pending / handled")
    private String status;

    @Schema(description = "处置结论：handled / rejected；未处置为 null")
    private String outcome;

    @Schema(description = "处置回执短标题（未处置为 null）")
    private String replyTitle;

    @Schema(description = "处置文案 / 不采纳原因（未处置为 null）")
    private String replyContent;

    @Schema(description = "处置人登录名（未处置 / 账号已删除为 null）")
    private String handledByName;

    @Schema(description = "处置时间（未处置为 null）")
    private LocalDateTime handledAt;
}
