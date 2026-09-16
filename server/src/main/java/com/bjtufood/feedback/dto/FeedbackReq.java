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
     * 反馈类型写入值域（P3-10 收敛，单一真源 {@code FeedbackConst.WRITABLE_TYPES}）：
     * suggestion / add / error / report。
     * bug / other 为历史遗留类型（端上已无生产者），禁止新增，非法值由 Service 层返回 400。
     */
    @Schema(description = "反馈类型：suggestion/add/error/report（bug/other 为历史遗留、禁新增）", example = "suggestion")
    @NotBlank(message = "反馈类型不能为空")
    private String type;

    /**
     * 二级分类（DEV-01 补全落库）：仅 {@code type=suggestion} 有效，值域 idea/problem
     * （单一真源 {@code FeedbackConst.SUB_WRITE_WHITELIST}）。
     * type 非 suggestion 时该值无效：未提供（null/空白）按未填处理（落库 NULL）；
     * 一旦提供（非空白）即 400（严格模式）；suggestion 场景下 provided 但值域非法同样 400。
     */
    @Schema(description = "二级分类：仅 suggestion 类型有效，值域 idea/problem（其他类型传值将 400）", example = "idea")
    private String sub;

    @Schema(description = "反馈内容", example = "希望增加更多素食档口")
    @NotBlank(message = "反馈内容不能为空")
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
