package com.bjtufood.moment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布 / 编辑动态请求
 */
@Data
@Schema(description = "发布动态请求")
public class MomentPublishReq {

    @Schema(description = "正文内容", example = "今天在面食窗口吃到超赞的牛肉拉面！")
    @Size(max = 500, message = "正文不能超过500字")
    private String content;

    /** 图片URL列表（≤9），与 images 逗号串互相转换 */
    @Schema(description = "图片URL列表（≤9张）")
    @Size(max = 9, message = "最多上传9张图片")
    private List<String> images;

    /** 关联对象类型：none（默认）/ dish / stall */
    @Schema(description = "关联对象类型：none/dish/stall", example = "none")
    private String relatedType = "none";

    /** 关联对象ID（relatedType≠none 时必填） */
    @Schema(description = "关联对象ID")
    private Long relatedId;
}
