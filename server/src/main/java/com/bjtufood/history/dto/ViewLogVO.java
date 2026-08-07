package com.bjtufood.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览足迹视图对象
 */
@Data
@Schema(description = "浏览足迹展示信息")
public class ViewLogVO {

    @Schema(description = "足迹ID")
    private Long id;

    @Schema(description = "浏览对象类型：dish/stall/canteen/moment")
    private String targetType;

    @Schema(description = "浏览对象ID")
    private Long targetId;

    @Schema(description = "对象名称")
    private String targetName;

    @Schema(description = "对象图片")
    private String targetImage;

    @Schema(description = "浏览时间")
    private LocalDateTime createdAt;
}
