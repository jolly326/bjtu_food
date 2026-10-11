package com.bjtufood.feedback.dto;

import com.bjtufood.feedback.constant.OptionConst;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增分类字典项请求（`feedback_option` / `report_option` 共用）。
 * <p>
 * 只填中文 `name`；选项 ID 由后端生成、是历史工单的数据锚点（工单表的 `option_id`），
 * 故**没有**「改 ID」的端点。
 */
@Data
@Schema(description = "新增分类字典项请求")
public class OptionSaveReq {

    @Schema(description = "中文名（1~32 字）", example = "程序功能Bug")
    @NotBlank(message = "中文名不能为空")
    @Size(max = OptionConst.NAME_MAX_LENGTH, message = "中文名不能超过 32 字")
    private String name;
}
