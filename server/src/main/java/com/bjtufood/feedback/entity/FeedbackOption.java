package com.bjtufood.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈类型字典实体（`feedback_option`）。
 * <p>
 * 意见反馈页的「反馈类型」单选的**可维护字典**（种子：程序功能Bug / 产品功能建议 / 其他相关问题）：
 * <ul>
 *   <li>{@link #id}：**数据锚点**（反馈行按它落库 {@code feedback.option_id}）；</li>
 *   <li>{@link #name}：中文名，**改名免费**（历史反馈的中文翻译随表实时生效）；</li>
 *   <li>{@link #status}：`on` / `off` —— 公开端点只下发启用项，提交白名单同样只认启用项；</li>
 *   <li>删除受引用约束：被任一反馈引用即 `400`（下线一律用停用）。</li>
 * </ul>
 * 口径真源：docs/schema/feedback_option.md 与 docs/api/web/feedback-options.md。
 */
@Data
@TableName("feedback_option")
@Schema(description = "反馈类型（dict）")
public class FeedbackOption implements OptionDictionaryItem {

    @TableId(type = IdType.AUTO)
    @Schema(description = "类型ID")
    private Long id;

    /** 中文名（可改，改名免费） */
    @Schema(description = "中文名", example = "程序功能Bug")
    private String name;

    /** 状态：on=启用 / off=停用 */
    @Schema(description = "状态：on=启用 / off=停用")
    private String status;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
