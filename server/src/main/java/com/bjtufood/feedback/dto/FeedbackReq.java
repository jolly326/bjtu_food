package com.bjtufood.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交反馈请求（替代原 Map 裸参）
 */
@Data
@Schema(description = "提交反馈请求")
public class FeedbackReq {

    /** 反馈类型：suggestion / error / other / report */
    @Schema(description = "反馈类型：suggestion/error/other/report", example = "suggestion")
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    @Schema(description = "反馈内容", example = "希望增加更多素食档口")
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 1000, message = "反馈内容不能超过1000字")
    private String content;

    /** 联系方式（选填） */
    @Schema(description = "联系方式（选填）")
    @Size(max = 128, message = "联系方式不能超过128字")
    private String contact;

    /** 附图（选填，已上传的绝对URL数组；截图/作证照片/菜品图，2026-08-17 新增） */
    @Schema(description = "附图（选填，绝对URL数组）")
    private List<String> images;

    /** 关联类型（举报场景）：moment；其他反馈可空 */
    @Schema(description = "关联类型（举报场景）：moment；其他反馈可空")
    private String relatedType;

    /** 关联对象ID（举报场景：被举报动态ID）；其他反馈可空 */
    @Schema(description = "关联对象ID（举报场景：动态ID）；其他反馈可空")
    private Long relatedId;
}
