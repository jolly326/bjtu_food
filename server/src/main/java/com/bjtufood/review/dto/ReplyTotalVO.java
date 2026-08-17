package com.bjtufood.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 楼中楼子回复数量统计（供窗口限制后判断是否有更多）。
 * 由 selectReplyTotalByParentIds 返回，仅内部使用，不直接序列化给前端。
 */
@Data
@Schema(description = "楼中楼子回复数量统计（内部使用）")
public class ReplyTotalVO {

    @Schema(description = "父评价ID")
    private Long parentId;

    @Schema(description = "该父评价的子回复总数")
    private Long total;
}
