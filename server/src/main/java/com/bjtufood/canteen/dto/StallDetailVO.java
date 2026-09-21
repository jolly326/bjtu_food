package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 档口节点视图对象（VO）
 * <p>
 * 仅用于 {@code GET /canteens?include=stalls} 的档口树节点。端上（反馈页位置两级联动）
 * **只读 id / name**，故出参收敛为 {id, name} 最小集（2026-09-21，见 docs/project_spec.md §7.33）。
 * <p>
 * 已下线字段：{@code dishCount} / {@code topDishes} / {@code perCapita}（2026-09-15 三端零消费）、
 * {@code images} / {@code location} / {@code floor} / {@code windowNo} / {@code description} /
 * {@code avgRating}（2026-09-21 零消费收敛——其中 avgRating 连带删除 CanteenServiceImpl 的
 * batchAvgRating 批查，属出参收敛后的白算）。
 */
@Data
@Schema(description = "档口节点展示信息")
public class StallDetailVO {

    @Schema(description = "档口ID")
    private Long id;

    @Schema(description = "档口名称", example = "面面俱到")
    private String name;
}
