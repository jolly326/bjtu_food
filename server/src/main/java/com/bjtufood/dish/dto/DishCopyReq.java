package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 复制菜品请求（{@code POST /admin/dishes/{id}/copy}）。
 * <p>
 * 契约见 docs/api/web/dishes.md：<b>只收新菜名</b>，其余字段全部复制源菜品；
 * 副本**默认下架**（`off`）—— 复制出来的是半成品，先下架、确认内容后再上架。
 */
@Data
@Schema(description = "复制菜品请求")
public class DishCopyReq {

    @Schema(description = "新菜品名称（1~64 字）", example = "牛肉拉面（大份）")
    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 64, message = "菜品名称不能超过 64 字")
    private String name;
}
