package com.bjtufood.feedback.dto;

import com.bjtufood.feedback.constant.OptionConst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类字典改名请求（**只改 `name`**，改名免费 —— 历史工单的中文翻译随表实时生效）。
 */
@Data
@Schema(description = "分类字典改名请求")
public class OptionRenameReq {

    @Schema(description = "中文名（1~32 字）", example = "程序功能Bug")
    @NotBlank(message = "中文名不能为空")
    @Size(max = OptionConst.NAME_MAX_LENGTH, message = "中文名不能超过 32 字")
    private String name;
}
