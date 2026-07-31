package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "后台轮播图展示信息")
public class BannerAdminVO {

    @Schema(description = "轮播图ID")
    private Long id;

    @Schema(description = "标题", example = "明湖餐厅推荐")
    private String title;

    @Schema(description = "副标题", example = "明湖附近的学生餐饮")
    private String subtitle;

    @Schema(description = "背景图片URL列表")
    private List<String> images;

    @Schema(description = "跳转类型（历史字段：dish/url）")
    private String type;

    @Schema(description = "跳转类型枚举：DISH/URL/NONE（ACTIVITY 已废弃）")
    private String targetType;

    @Schema(description = "跳转目标ID（target_type=DISH/ACTIVITY时使用）")
    private Long targetId;

    @Schema(description = "跳转目标URL（type=url时使用）")
    private String targetUrl;

    @Schema(description = "关联食堂ID（可选）")
    private Long canteenId;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    @Schema(description = "状态（enabled/disabled）")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
