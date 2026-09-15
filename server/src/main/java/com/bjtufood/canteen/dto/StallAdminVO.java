package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "后台档口列表展示信息")
public class StallAdminVO {

    @Schema(description = "档口ID")
    private Long id;

    @Schema(description = "所属食堂ID")
    private Long canteenId;

    @Schema(description = "档口名称", example = "面食窗口")
    private String name;

    @Schema(description = "档口位置")
    private String location;

    @Schema(description = "楼层（如 1F/2F）", example = "1F")
    private String floor;

    @Schema(description = "窗口号", example = "3号窗口")
    private String windowNo;

    @Schema(description = "档口描述")
    private String description;

    @Schema(description = "档口展示图片列表")
    private List<String> images;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    // 2026-09-15 字段下线：createdBy（提交人用户ID）web 后台零消费——
    // 管理端为单口令模型、无真实身份（写入侧恒为系统占位 0），对外暴露无意义，已删除。

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
