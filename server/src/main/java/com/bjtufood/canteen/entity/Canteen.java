package com.bjtufood.canteen.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食堂实体类
 * <p>
 * 对应数据库表：canteen。用户拍板：食堂已<b>去实体化</b>，降级为「菜品筛选属性字典」，
 * 生命周期仅「新增 / 改名（编辑）」，不具备删除、停业/营业状态、营业时间、实体审核等实体语义能力。
 */
@Data
@TableName("canteen")
@Schema(description = "食堂")
public class Canteen {

    @TableId(type = IdType.AUTO)
    @Schema(description = "食堂ID")
    private Long id;

    /** 食堂名称 */
    @Schema(description = "食堂名称", example = "第一食堂")
    private String name;

    /** 食堂图片 URL 列表，JSON 字符串 */
    @Schema(description = "食堂图片URL列表JSON")
    private String images;

    /** 食堂位置 */
    @Schema(description = "食堂位置")
    private String location;

    /** 食堂描述 */
    @Schema(description = "食堂描述")
    private String description;

    /** 排序权重（数字越小越靠前） */
    @Schema(description = "排序权重")
    private Integer sortOrder;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
