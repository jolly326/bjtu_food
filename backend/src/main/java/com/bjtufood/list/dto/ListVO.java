package com.bjtufood.list.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清单视图对象（列表用）
 * <p>
 * 展示用户创建的美食清单摘要信息
 */
@Data
@Schema(description = "清单摘要信息（列表用）")
public class ListVO {

    @Schema(description = "清单ID")
    private Long id;

    @Schema(description = "清单名称", example = "一食堂必吃TOP3")
    private String name;

    @Schema(description = "清单描述")
    private String description;

    @Schema(description = "包含菜品数", example = "3")
    private Integer dishCount;

    @Schema(description = "分享token", example = "abc123def456")
    private String shareToken;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
