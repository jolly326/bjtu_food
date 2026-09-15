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

    @Schema(description = "档口描述", example = "第一食堂·面面俱到，为您提供美味的校园餐饮体验。")
    private String description;

    @Schema(description = "档口平均评分（取该档口下所有菜品评价的平均值，1-5星，无评价为 0.00）", example = "4.50")
    private BigDecimal avgRating;

    // 2026-09-15 字段下线：dishCount / topDishes / perCapita 三端零消费
    //（client 侧档口节点仅消费 name；端上档口卡不展示菜品数/招牌菜/人均），
    // 连带删除 CanteenServiceImpl 的白算逻辑（含每次 /canteens/all 的「在售菜品批量 IN 查询」）。
}
