package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 食堂列表视图对象（VO）
 * <p>
 * 首页食堂列表展示信息
 */
@Data
@Schema(description = "食堂列表展示信息")
public class CanteenInfoVO {

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

    /** 与用户位置的距离（米，haversine 直线距离）；未传定位或无坐标时为 null */
    @Schema(description = "与用户位置的距离（米，按定位排序时返回）")
    private Integer distance;
}
