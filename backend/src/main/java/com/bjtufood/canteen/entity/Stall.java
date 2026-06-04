package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 档口实体类
 * <p>
 * 对应数据库表：stall
 * 例如：第一食堂下属的"面食窗口"、"盖饭窗口"
 */
@Data
@TableName("stall")
@Schema(description = "档口")
public class Stall {

    @TableId(type = IdType.AUTO)
    @Schema(description = "档口ID")
    private Long id;

    /** 所属食堂ID */
    @Schema(description = "所属食堂ID")
    private Long canteenId;

    /** 档口名称 */
    @Schema(description = "档口名称", example = "面食窗口")
    private String name;

    /** 档口多图，JSON 字符串 */
    @Schema(description = "档口多图JSON")
    private String images;

    /** 档口位置 */
    @Schema(description = "档口位置")
    private String location;

    /** 档口描述 */
    @Schema(description = "档口描述")
    private String description;

    /** 平均评分 */
    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    /** 排序权重 */
    @Schema(description = "排序权重")
    private Integer sortOrder;

    /** 状态：open / closed */
    @Schema(description = "状态", example = "open")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
