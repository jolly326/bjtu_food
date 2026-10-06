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

    /** 其下档口数（联表统计，供删除前判断与列表展示；口径见 docs/api/web/stalls.md） */
    @Schema(description = "其下档口数", example = "3")
    private Long stallCount;

    @Schema(description = "食堂位置")
    private String location;

    @Schema(description = "食堂描述")
    private String description;

    @Schema(description = "食堂图片URL列表")
    private List<String> images;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
