package com.bjtufood.list.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建美食清单请求参数")
public class ListCreateReq {

    @NotBlank(message = "清单名称不能为空")
    @Size(max = 100, message = "清单名称不能超过100字")
    @Schema(description = "清单名称", example = "明湖餐厅必吃", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "清单描述", example = "适合第一次来明湖餐厅的同学")
    private String description;

    @NotEmpty(message = "至少选择1个菜品")
    @Schema(description = "菜品ID列表", example = "[1, 2, 6]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> dishIds;
}
