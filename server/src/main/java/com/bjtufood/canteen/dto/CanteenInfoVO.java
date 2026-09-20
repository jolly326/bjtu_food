package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 食堂列表视图对象（VO）
 * <p>
 * 首页食堂列表展示信息。坐标字段 latitude / longitude 已于 2026-09-20 拍板全链下线
 * （学生端不申请定位权限、不计算距离），位置表达收敛为 食堂 · 楼层 · 档口名。
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
}
