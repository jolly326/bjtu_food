package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "后台菜品新增/编辑请求参数")
@Deprecated(since = "2026-09", forRemoval = true)
public class DishAdminReq {
    // ⚠️ 冻结：管理端（Web 后台）请求对象，待后期整体重构时移除。本期保留可编译，不删除。

    /**
     * 所属档口ID。
     * 注意：DTO 层不做 @NotNull 校验——PUT /admin/dishes/{id} 支持「部分更新」，
     * 前端行内状态 Switch（仅传 status）依赖此能力；新增（POST）时的必填校验在 Service 层完成。
     */
    @Schema(description = "所属档口ID", example = "1")
    private Long stallId;

    /**
     * 档口名称（§7.23 第 1 条：食堂/档口随菜品按名 upsert）。
     * 传有效名称时优先生效：字典存在同名档口则复用其 ID，不存在则由后端自动建档（同名不重复建档）；
     * 「其他」等空值语义名称不建档（视为未传，回退 stallId 逻辑）。
     */
    @Schema(description = "档口名称（按名 upsert：存在则复用，不存在则自动建档；「其他」等空值不建档）", example = "面食窗口")
    private String stallName;

    /**
     * 食堂名称：仅当按 stallName 新建档口时消费——
     * 有效名称按名 upsert 食堂并作为新档口的所属食堂；「其他」等空值/未传则新档口不挂食堂（canteen_id=0）。
     */
    @Schema(description = "食堂名称（仅当 stallName 触发新建档口时按名 upsert；「其他」等空值不建档）", example = "第一食堂")
    private String canteenName;

    @Schema(description = "菜品名称", example = "番茄炒蛋盖饭")
    private String name;

    @Schema(description = "现价，单位：分（已含折扣）。1200 表示 12 元。", example = "1200")
    private Integer price;

    @Schema(description = "原价（分，可空）。1500 表示 15 元；originalPrice > price 视为有折扣。", example = "1500")
    private Integer originalPrice;

    @Schema(description = "菜品描述", example = "学生餐厅常见基础套餐")
    private String description;

    @Schema(description = "菜品图片 URL 列表。单图时只放一个 URL。", example = "[\"/images/seed/dishes/tomato-egg.jpg\"]")
    private List<String> images;

    /**
     * 描述属性（动态属性模型）：键 = 维度 {@code fieldKey}（camelCase），
     * 值 = **中文文本**（{@code single} 维度为字符串 / {@code multi} 维度为字符串数组）。
     * 取值为自由文本，候选仅作提示（无独立取值字典表）。
     */
    @Schema(description = "描述属性（键=维度 fieldKey，值=中文文本/数组；可空）",
            example = "{\"dietType\":\"半荤\",\"ingredients\":[\"蛋\"],\"flavorTags\":[\"酸\",\"甜\"],\"serveTemp\":\"热食\"}")
    private Map<String, Object> attributes;

    @Schema(description = "菜品大类枚举键（值域见 GET /dishes/views 的大类视图；可空；服务端白名单校验非法值 400）", example = "noodle")
    private String mealType;

    @Schema(description = "状态：on=上架，off=下架", example = "on")
    private String status;
}
