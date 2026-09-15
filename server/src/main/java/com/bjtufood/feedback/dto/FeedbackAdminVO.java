package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈管理端视图对象
 */
@Data
@Schema(description = "反馈管理端展示信息")
public class FeedbackAdminVO {

    @Schema(description = "反馈ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String userNickname;

    @Schema(description = "反馈类型：suggestion/add/error/bug/report/other")
    private String type;

    @Schema(description = "二级分类（仅 suggestion 有效）：idea=想法/problem=问题；其他类型与历史存量为 null")
    private String sub;

    @Schema(description = "反馈内容")
    private String content;

    @Schema(description = "反馈配图 URL 列表（COS 绝对地址，≤3 张）")
    private List<String> images;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "关联类型：举报为 review；信息纠错为 dish；其他为 null")
    private String relatedType;

    @Schema(description = "关联对象ID（举报：评价ID；信息纠错：菜品ID）；其他为 null")
    private Long relatedId;

    /**
     * 关联菜品名称（DEV-04）：仅 {@code relatedType='dish'}（信息纠错）时填充，
     * 由服务端按 related_id 批量查 dish 表补全（<b>不区分上/下架</b>，含已下架菜品，供管理端回看纠错对象）；
     * 其他关联类型、relatedId 为空、或菜品已被物理删除时保持 null。
     * <p>
     * 后台管理端点对管理端开放，故此处可安全呈现下架菜品名——端上不得据此推断菜品可见性。
     */
    @Schema(description = "关联菜品名称（仅 relatedType=dish 时填充，含已下架菜品；否则为 null）")
    private String relatedDishName;

    @Schema(description = "处理状态：pending/handled")
    private String status;

    /**
     * 处理结论（§7.23 第 5 条）：{@code handled}=通过/已处理（缺省）；{@code rejected}=不采纳/退回。
     * <p>
     * 派生口径（user_feedback 表无 outcome 物理列，落库语义由 handle() 保证一致：
     * rejected 结论时 reject_reason 必填非空，handled 结论时 reject_reason 恒为 NULL）：
     * <ul>
     *   <li>status=handled 且 rejectReason 非空 → {@code rejected}；</li>
     *   <li>status=handled 且 rejectReason 为空 → {@code handled}（含历史存量：reject_reason 列后补，
     *       老的处理记录该列为 NULL，派生即「已处理」，与缺省语义一致）；</li>
     *   <li>status=pending（未处理）→ {@code null}，前端按缺省「已处理」以外的未处理态展示。</li>
     * </ul>
     */
    @Schema(description = "处理结论：handled=通过/已处理（缺省）；rejected=不采纳/退回；pending 未处理为 null")
    private String outcome;

    @Schema(description = "管理员回复")
    private String reply;

    @Schema(description = "不采纳原因（处理结论为不采纳/退回时非空，1~200 字）")
    private String rejectReason;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;
}
