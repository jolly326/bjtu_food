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

    /** 反馈类型：suggestion / add / error / bug / report / other（与 FeedbackConst 保持一致） */
    @Schema(description = "反馈类型：suggestion/add/error/bug/report/other", example = "suggestion")
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    @Schema(description = "反馈内容", example = "希望增加更多素食档口")
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 1000, message = "反馈内容不能超过1000字")
    private String content;

    @Size(max = 3, message = "反馈配图最多 3 张")
    @Schema(description = "反馈配图 URL 列表（经 POST /upload/images 转存的 COS 绝对地址，≤3 张）")
    private List<String> images;

    /** 联系方式（选填） */
    @Schema(description = "联系方式（选填）")
    @Size(max = 128, message = "联系方式不能超过128字")
    private String contact;

    /** 关联类型：report 举报为 review（被举报评价）；error 信息纠错为 dish；其他反馈可空 */
    @Schema(description = "关联类型：举报为 review；信息纠错为 dish；其他可空")
    private String relatedType;

    /** 关联对象ID（举报场景：被举报评价ID；信息纠错：菜品ID）；其他反馈可空 */
    @Schema(description = "关联对象ID（举报场景：评价ID）；其他反馈可空")
    private Long relatedId;
}
