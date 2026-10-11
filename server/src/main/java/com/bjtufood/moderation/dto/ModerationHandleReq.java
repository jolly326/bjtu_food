package com.bjtufood.moderation.dto;

import com.bjtufood.moderation.constant.ModerationConst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处置请求（**三类工单共用**：{@code PUT /admin/feedbacks/{id}}、{@code PUT /admin/reports/{id}}、
 * {@code PUT /admin/corrections/{id}}）。
 * <p>
 * 只接受 JSON body：
 * <ul>
 *   <li>{@code replyTitle} 回执短标题（**可选**，≤40 字；留空由服务端按类型与结论补缺省文案）；</li>
 *   <li>{@code replyContent} 处置文案（**可选**，≤600 字；留空同上。但
 *       {@code outcome=rejected} 时**必填**——不采纳必须写清原因，纯空白 → 400）；</li>
 *   <li>{@code outcome} 处置结论：{@code handled}=通过/已处理（缺省）；{@code rejected}=不采纳/退回。
 *       非法值由 Service 层 400 拦截；</li>
 *   <li>{@code hideReview} **仅举报处置消费**：通过时联动隐藏被举报评价。</li>
 * </ul>
 * 「处置文案」在处置任务表中是<b>单一列</b>（不采纳原因并入正文），故本请求体只有标题 + 文案 + 结论三项。
 */
@Data
@Schema(description = "处置请求（三类工单共用）")
public class ModerationHandleReq {

    @Schema(description = "回执短标题（可选，≤40 字；留空用缺省文案）", example = "反馈已处理")
    @Size(max = ModerationConst.REPLY_TITLE_MAX_LENGTH, message = "回执标题不能超过40字")
    private String replyTitle;

    @Schema(description = "处置文案（可选，≤600 字；outcome=rejected 时必填，即为不采纳原因）",
            example = "该问题已在近期版本修复")
    @Size(max = ModerationConst.REPLY_CONTENT_MAX_LENGTH, message = "处置文案不能超过600字")
    private String replyContent;

    @Schema(description = "处置结论：handled=通过/已处理（缺省）；rejected=不采纳/退回", example = "handled")
    private String outcome;

    @Schema(description = "仅举报处置消费：通过时是否联动隐藏被举报评价（缺省 false）", example = "true")
    private Boolean hideReview;
}
