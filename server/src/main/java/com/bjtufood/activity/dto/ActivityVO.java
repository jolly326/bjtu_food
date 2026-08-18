package com.bjtufood.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动展示 VO（小程序「最新活动」页卡片）
 */
@Data
@Schema(description = "最新活动")
public class ActivityVO {

    @Schema(description = "活动ID")
    private Long id;

    @Schema(description = "活动/文章标题")
    private String title;

    @Schema(description = "摘要（卡片副文案）")
    private String description;

    @Schema(description = "封面图 URL（可空）")
    private String image;

    @Schema(description = "公众号文章链接（web-view 打开）")
    private String articleUrl;

    @Schema(description = "发布时间（创建时间）")
    private LocalDateTime publishTime;
}
