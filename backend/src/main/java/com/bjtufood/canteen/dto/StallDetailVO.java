package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 档口详情视图对象（VO）
 * <p>
 * 前端档口详情页展示信息
 */
@Data
@Schema(description = "档口详情展示信息")
public class StallDetailVO {

    @Schema(description = "档口ID")
    private Long id;

    @Schema(description = "档口名称", example = "面面俱到")
    private String name;

    @Schema(description = "档口展示图片列表")
    private List<String> images;

    @Schema(description = "档口位置", example = "第一食堂")
    private String location;

    @Schema(description = "楼层（如 1F/2F）", example = "1F")
    private String floor;

    @Schema(description = "窗口号", example = "3号窗口")
    private String windowNo;

    @Schema(description = "营业时间，如 10:00-20:00", example = "10:00-20:00")
    private String businessHours;

    @Schema(description = "档口描述", example = "第一食堂·面面俱到，为您提供美味的校园餐饮体验。")
    private String description;

    @Schema(description = "档口平均评分（取该档口下所有菜品评价的平均值，1-5星，无评价为 0.00）", example = "4.50")
    private BigDecimal avgRating;

    @Schema(description = "档口菜品数")
    private Integer dishCount;

    @Schema(description = "档口主要菜品（评分前3，名称）")
    private List<String> topDishes;

    /** 人均消费（元，展示用）。由该档口在售菜品成交价（有促销价取促销价，否则取原价）的中位数转元取整派生；无在售菜品时为 null。 */
    @Schema(description = "人均消费（元，展示用，已为元）", example = "15")
    private Integer perCapita;
}
