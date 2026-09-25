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

    /**
     * 反馈类型写入值域（单一真源 {@code FeedbackConst.WRITABLE_TYPES}）：
     * issue（我要反馈问题）/ report（举报）。
     * suggestion/add/error/bug/other 为历史遗留类型（端上已无生产者），禁止新增，非法值由 Service 层返回 400。
     */
    @Schema(description = "反馈类型：issue=我要反馈问题 / report=举报（历史类型禁新增）", example = "issue")
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    /**
     * 二级分类（按 type 分流，单一真源 {@code FeedbackConst}）：
     * report → 举报原因（**必选**，值域 = {@code REPORT_REASON_VALUES}，
     * 字典端点 {@code GET /feedback/report-reasons} 下发）；其他类型禁带（提供即 400）。
     */
    @Schema(description = "二级分类：report 传举报原因 value（必选，见 GET /feedback/report-reasons）；其他类型禁带", example = "spam")
    private String sub;

    @Schema(description = "反馈内容（report 类型可空——举报以结构化原因单选为准）", example = "希望增加更多素食档口")
    @Size(max = 1000, message = "反馈内容不能超过1000字")
    private String content;

    @Size(max = 3, message = "反馈配图最多 3 张")
    @Schema(description = "反馈配图 URL 列表（经 POST /upload/images 转存的 COS 绝对地址，≤3 张）")
    private List<String> images;

    // contact 字段已于 2026-09-16 产品定型「不收集联系方式」删除：user_feedback.contact 列、
    // 实体字段与落库逻辑同批退役；端上请求不再携带该字段（携带亦被忽略）。

    /** 关联类型：report 举报为 review（被举报评价）；error 信息纠错为 dish；其他反馈可空 */
    @Schema(description = "关联类型：举报为 review；信息纠错为 dish；其他可空")
    private String relatedType;

    /** 关联对象ID（举报场景：被举报评价ID；信息纠错：菜品ID）；其他反馈可空 */
    @Schema(description = "关联对象ID（举报场景：评价ID）；其他反馈可空")
    private Long relatedId;
}
