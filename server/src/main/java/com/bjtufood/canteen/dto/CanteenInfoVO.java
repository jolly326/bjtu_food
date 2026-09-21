package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 食堂列表视图对象（VO）
 * <p>
 * 首页食堂列表展示信息。坐标字段 latitude / longitude 已于 2026-09-20 拍板全链下线
 * （学生端不申请定位权限、不计算距离），位置表达收敛为 食堂 · 楼层 · 档口名。
 * <p>
 * 出参收敛（2026-09-21，见 docs/project_spec.md §7.33）：{@code location} / {@code description} /
 * {@code images} 三字段**全端零消费**（小程序只读 id / name；管理端走 /admin/canteens 的
 * CanteenAdminVO，不经本 VO），已删除——本 VO 恰为 {id, name} 最小字典。
 * 注意区分：数据库列 {@code canteen.location} 仍保留（留档），与「是否作为公开出参」是两件事。
 */
@Data
@Schema(description = "食堂列表展示信息")
public class CanteenInfoVO {

    @Schema(description = "食堂ID")
    private Long id;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String name;
}
