package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
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

    /** 食堂坐标（GCJ-02）；前端本地 Haversine 算「距你 Xm」用，服务器不再计算距离 */
    @Schema(description = "食堂纬度（GCJ-02），前端本地算距离用", example = "39.90")
    private BigDecimal latitude;

    /** 食堂经度（GCJ-02） */
    @Schema(description = "食堂经度（GCJ-02），前端本地算距离用", example = "116.40")
    private BigDecimal longitude;
}
