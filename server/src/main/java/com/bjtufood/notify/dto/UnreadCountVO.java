package com.bjtufood.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未读通知总数出参（{@code GET /my/notifications/unread-count}）。
 * <p>
 * 以具名 VO 承载（替代匿名 Map），使 OpenAPI 文档可生成精确 schema；
 * JSON 结构不变（仍是 {@code { "count": n }}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "未读通知总数")
public class UnreadCountVO {

    @Schema(description = "未读通知条数", example = "3")
    private Long count;
}
