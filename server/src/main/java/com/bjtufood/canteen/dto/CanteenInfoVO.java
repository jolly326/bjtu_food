package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 食堂列表视图对象（VO）
 * <p>
 * 最小字典出参（2026-09-21 §7.33 / change {@code api-slimming}）：端上消费方仅读
 * {@code id} / {@code name}（首页 / 搜索筛选条按 id 筛选、按 name 回显）。
 * 已下线字段（不得回流）：{@code latitude} / {@code longitude}（2026-09-20 坐标全链下线）、
 * {@code location} / {@code description} / {@code images}（2026-09-21 全端零消费收敛——
 * 管理端走 {@code CanteenAdminVO}，不经本类；§7.31 保留的是 canteen 表的 location 列，与出参是两回事）。
 */
@Data
@Schema(description = "食堂列表展示信息")
public class CanteenInfoVO {

    @Schema(description = "食堂ID")
    private Long id;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String name;
}
