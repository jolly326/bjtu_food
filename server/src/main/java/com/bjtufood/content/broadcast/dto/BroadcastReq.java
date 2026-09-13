package com.bjtufood.content.broadcast.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台广播新增/编辑请求（P3/ARCH-008：替代原「实体直接收请求体」）
 * <p>
 * 字段与原 Broadcast 实体请求侧一一对应（id/createdAt/updatedAt 由服务端管理，
 * 不在请求体），请求 JSON 结构对既有前端零破坏；未知字段遵循 Spring Boot
 * 默认策略忽略。
 * <p>
 * 校验分组说明：Web 后台「行内启停」以 {@code {"status": ...}} 部分字段 PUT 更新
 * （web/src/views/content/BroadcastManage.vue toggleStatus），故 @NotBlank 必填
 * 仅挂新增分组 {@link OnCreate}（create 端点以 {@code @Validated({Default.class,
 * OnCreate.class})} 同时启用两组）；编辑走 Default 组只做长度/枚举格式校验，
 * null 字段跳过（MyBatis-Plus NOT_NULL 更新策略），保持部分更新语义。
 */
@Data
@Schema(description = "后台广播新增/编辑请求")
public class BroadcastReq {

    /** 新增场景校验分组：create 全量必填；update 不挂载（允许部分字段更新） */
    public interface OnCreate {
    }

    @Schema(description = "广播标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(groups = OnCreate.class, message = "广播标题不能为空")
    @Size(max = 128, message = "广播标题不能超过128字")
    private String title;

    @Schema(description = "广播正文（ticker 展示文本）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(groups = OnCreate.class, message = "广播正文不能为空")
    @Size(max = 512, message = "广播正文不能超过512字")
    private String content;

    @Schema(description = "广播类型：NOTICE/ACTIVITY/DISH/URL/NONE", example = "NOTICE")
    @NotBlank(groups = OnCreate.class, message = "广播类型不能为空")
    @Pattern(regexp = "NOTICE|ACTIVITY|DISH|URL|NONE", message = "广播类型需为 NOTICE/ACTIVITY/DISH/URL/NONE")
    private String broadcastType;

    @Schema(description = "跳转目标ID（DISH 类型时填菜品ID）")
    private Long targetId;

    @Schema(description = "跳转目标URL（URL 类型时填外链）")
    @Size(max = 512, message = "跳转URL不能超过512字")
    private String targetUrl;

    @Schema(description = "排序权重（越小越靠前）")
    private Integer sortOrder;

    @Schema(description = "状态：enabled/disabled", example = "enabled")
    @Pattern(regexp = "enabled|disabled", message = "状态需为 enabled/disabled")
    private String status;
}
