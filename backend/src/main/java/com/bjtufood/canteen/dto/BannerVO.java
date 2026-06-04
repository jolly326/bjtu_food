package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 轮播图视图对象（VO）
 * <p>
 * 首页轮播图展示信息
 */
@Data
@Schema(description = "轮播图展示信息")
public class BannerVO {

    @Schema(description = "标题", example = "交大美食季")
    private String title;

    @Schema(description = "副标题", example = "发现校园里的每一道美味")
    private String subtitle;

    @Schema(description = "背景图片URL列表")
    private List<String> images;
}
