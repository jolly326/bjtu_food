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

    /** 所属食堂名（联表带出，列表直接可读；口径见 docs/api/web/stalls.md） */
    @Schema(description = "所属食堂名", example = "第一食堂")
    private String canteenName;

    /** 其下菜品数（跨域计数由 controller 编排填充；供删除前判断） */
    @Schema(description = "其下菜品数", example = "12")
    private Long dishCount;

    @Schema(description = "档口名称", example = "面食窗口")
    private String name;

    @Schema(description = "档口位置")
    private String location;

    @Schema(description = "楼层（受控字典、值即汉字：负一层/一层/二层/三层/四层）", example = "二层")
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

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
