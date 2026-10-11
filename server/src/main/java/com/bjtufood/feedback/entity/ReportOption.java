package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报原因字典实体（`report_option`）。
 * <p>
 * 举报弹层「原因」单选的**可维护字典**：
 * <ul>
 *   <li>{@link #id}：**数据锚点**（举报行按它落库 {@code report.option_id}）；</li>
 *   <li>{@link #name}：中文名，**改名免费**（历史举报的中文翻译随表实时生效）；</li>
 *   <li>{@link #status}：`on` / `off` —— 公开端点只下发启用项，提交白名单同样只认启用项；</li>
 *   <li>删除受引用约束：被任一举报引用即 `400`（下线一律用停用）。</li>
 * </ul>
 * 口径真源：docs/schema/report_option.md 与 docs/api/web/report-options.md。
 */
@Data
@TableName("report_option")
@Schema(description = "举报原因（dict）")
public class ReportOption implements OptionDictionaryItem {

    @TableId(type = IdType.AUTO)
    @Schema(description = "原因ID")
    private Long id;

    /** 中文名（可改，改名免费） */
    @Schema(description = "中文名", example = "垃圾广告 / 营销刷屏")
    private String name;

    /** 状态：on=启用 / off=停用 */
    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
