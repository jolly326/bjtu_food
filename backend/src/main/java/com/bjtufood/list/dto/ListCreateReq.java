package com.bjtufood.list.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建清单请求 DTO
 * <p>
 * POST /api/lists 的请求体
 */
@Data
@Schema(description = "创建清单请求参数")
public class ListCreateReq {

    @NotBlank(message = "清单名称不能为空")
    @Size(max = 100, message = "清单名称不能超过100字符")
    @Schema(description = "清单名称", example = "一食堂必吃TOP3", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "清单描述", example = "我最爱的三个菜")
    private String description;

    @NotEmpty(message = "至少选择1个菜品")
    @Schema(description = "菜品ID列表", example = "[1, 3, 5]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> dishIds;
}
