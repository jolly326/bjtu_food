package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 菜品描述属性<b>维度定义</b>（公开只读字典出参，{@code GET /dishes/attributes}）。
 * <p>
 * <b>2026-09-29 新增</b>：web 端此前调用的 {@code GET /dishes/attributes} <b>后端从未存在</b>
 * （只有按单菜的 {@code /dishes/{id}/attributes}），管理后台的「描述四维录入选项」长期 404。
 * <p>
 * <b>与 {@link DishAttributeEditVO} 的差异</b>：后者按<b>单菜现有维度</b>下发编辑候选；
 * 本 VO 下发字典表中<b>全部维度定义 + 参考候选</b>，供管理端录入表单与筛选器使用。
 * <p>
 * <b>公开只读而非管理端专属</b>：数据为非敏感公开枚举，两端复用符合业界主流
 * （一个 API + 两种鉴权）。管理端的<b>写操作</b>仍全部走 {@code /admin/**}。
 * <p>
 * 端上零硬编码：不得自行维护任何「机器值 → 中文」映射或选项清单。
 *
 * @see com.bjtufood.dish.entity.DishAttributeDimension 维度表实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜品描述属性维度定义（公开字典）")
public class DishAttributeDefVO {

    @Schema(description = "维度ID", example = "1")
    private Long id;

    @Schema(description = "维度键（**恒等于**菜品 attributes 的键，camelCase）", example = "dietType")
    private String fieldKey;

    @Schema(description = "维度中文名（端上直接渲染）", example = "饮食属性")
    private String name;

    @Schema(description = "取值类型：single=单值 / multi=多值", example = "single")
    private String valueType;

    @Schema(description = "维度展示顺序（升序）", example = "1")
    private Integer order;

    @Schema(description = "该维度的参考候选值（该维度全库已用值去重、按使用频次倒序）；"
            + "**仅为参考、不构成约束**，空数组表示暂无参考值（端上仍可自由输入）")
    private List<String> options;
}