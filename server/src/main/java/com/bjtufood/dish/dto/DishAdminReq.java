package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "后台菜品新增/编辑请求参数")
public class DishAdminReq {

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

    /**
     * 搜索别名（逗号分隔，可空）。搜索关键词命中别名也能找到该菜品；
     * 支持中英文逗号分隔，后端保存前 trim 去空项并去重，总长 ≤255（超限 400）。
     * 注意：DTO 层不做校验——PUT /admin/dishes/{id} 支持部分更新（仅传 status 等），
     * alias 为 null 表示不修改；传空串表示清空别名（由 Service 显式落 NULL）。
     */
    @Schema(description = "搜索别名，逗号分隔（可空；null=不修改，空串=清空）", example = "冒菜,麻辣拌")
    private String alias;

    @Schema(description = "现价，单位：分（已含折扣）。1200 表示 12 元。", example = "1200")
    private Integer price;

    @Schema(description = "原价（分，可空）。1500 表示 15 元；originalPrice > price 视为有折扣。", example = "1500")
    private Integer originalPrice;

    @Schema(description = "菜品描述", example = "学生餐厅常见基础套餐")
    private String description;

    @Schema(description = "菜品图片 URL 列表。单图时只放一个 URL。", example = "[\"/images/seed/dishes/tomato-egg.jpg\"]")
    private List<String> images;

    // ==================== 描述四维（§7.28；原 tags/spiceLevel/region 已下线） ====================

    @Schema(description = "荤素/饮食属性：meat=荤 / half=半荤 / veg=素 / halal=清真（可空）", example = "half")
    private String dietType;

    @Schema(description = "主料/食材（逗号分隔）：pork/beef/lamb/chicken/duck/fish/egg/tofu/mushroom/veg/noodle/rice（可空）", example = "chicken,rice")
    private String ingredients;

    @Schema(description = "口味（逗号分隔）：spicy/numbing/sour/sweet/salty/umami/light/heavy（可空）", example = "spicy,sour")
    private String flavorTags;

    @Schema(description = "冷热：hot=热食 / room=常温 / ice=冰（可空）", example = "hot")
    private String serveTemp;

    @Schema(description = "状态：on=上架，off=下架", example = "on")
    private String status;

    /**
     * 菜品大类（2026-09-21 §7.34；单值枚举，后台以**下拉选择**录入，不允许自由输入）。
     * null = 不修改（编辑路径部分更新语义）；取值须命中 {@code MealTypeConst} 白名单。
     */
    @Schema(description = "菜品大类（单值）：set_meal 套餐盖饭 / stir_fry 家常小炒 / noodle 面食粉类 / dry_pot 香锅干锅 / snack 风味小吃 / soup_drink 汤饮甜品（可空）", example = "stir_fry")
    private String mealType;
}
