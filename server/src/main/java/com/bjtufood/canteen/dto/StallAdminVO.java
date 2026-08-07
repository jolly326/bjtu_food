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

    @Schema(description = "营业时间，如 10:00-20:00", example = "10:00-20:00")
    private String businessHours;

    @Schema(description = "档口描述")
    private String description;

    @Schema(description = "档口展示图片列表")
    private List<String> images;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    @Schema(description = "状态（open/closed）")
    private String status;

    @Schema(description = "审核状态：pending/approved/rejected")
    private String auditStatus;

    @Schema(description = "退回原因（audit_status=rejected 时由后台填写）")
    private String rejectReason;

    @Schema(description = "提交人用户ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
