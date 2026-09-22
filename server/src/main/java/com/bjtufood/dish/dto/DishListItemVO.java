package com.bjtufood.dish.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品列表行视图对象（**列表专用，恰为 8 个字段**）
 * <p>
 * 2026-09-22 用户拍板「列表 / 详情出参拆分」（见 docs/feature/client-首页菜品浏览.md D 项）：
 * 拆分前列表与详情共用 {@link DishDetailVO} 的 15 字段，其中 8 个在列表链路零消费
 * （description / images[1..] / floor / ratingCount / dietType / ingredients / flavorTags / serveTemp），
 * 20 行/页约多下发 160 个字段值 → 拆出本类，列表只装「首页卡片四段 + 搜索结果卡」的真实渲染集合。
 * <p>
 * 字段集：{@code id}（列表 key / 跳转）、{@code name}、{@code coverImage}（原 {@code images[0]}，
 * 无图为空串——列表只渲染首图，不再下发图片数组）、{@code price}、{@code originalPrice}、
 * {@code avgRating}（卡片评分；列表不发 {@code ratingCount}）、{@code canteenName}、{@code stallName}。
 * <p>
 * 详情专属字段（不得回流到本类）：{@code description} / {@code images} / {@code floor} /
 * {@code ratingCount} / {@code dietType} / {@code ingredients} / {@code flavorTags} /
 * {@code serveTemp} / {@code ratingDistribution}——它们只由 {@code GET /dishes/{id}} 下发。
 */
@Data
@Schema(description = "菜品列表行（公开 8 字段）")
public class DishListItemVO {

    @Schema(description = "菜品ID")
    private Long id;

    @Schema(description = "菜品名称", example = "牛肉拉面")
    private String name;

    /** 数据库原始 images JSON 字符串，由 Service 层解析出首图后填充 coverImage（不出参） */
    @JsonIgnore
    @Schema(hidden = true)
    private String imagesJson;

    /** 封面图（绝对 URL；无图为空串）——列表只渲染首图 */
    @Schema(description = "封面图URL（原 images[0]，无图为空串）")
    private String coverImage;

    /** 现价（分），唯一价格数据源；前端自行转换显示为元 */
    @Schema(description = "现价（分，已含折扣）", example = "1200")
    private Integer price;

    /** 原价（分，可空）；originalPrice > price 视为有折扣 */
    @Schema(description = "原价（分，可空）", example = "1500")
    private Integer originalPrice;

    @Schema(description = "平均评分", example = "4.5")
    private BigDecimal avgRating;

    @Schema(description = "食堂名称", example = "第一食堂")
    private String canteenName;

    @Schema(description = "档口名称", example = "面食窗口")
    private String stallName;
}
