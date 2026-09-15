package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "后台食堂列表展示信息")
public class CanteenAdminVO {

    @Schema(description = "食堂ID")
    private Long id;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String name;

    @Schema(description = "食堂位置")
    private String location;

    @Schema(description = "食堂描述")
    private String description;

    @Schema(description = "食堂图片URL列表")
    private List<String> images;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    // 2026-09-15 字段下线：createdBy（提交人用户ID）web 后台零消费——
    // 管理端为单口令模型、无真实身份（写入侧恒为系统占位 0），对外暴露无意义，已删除。

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
