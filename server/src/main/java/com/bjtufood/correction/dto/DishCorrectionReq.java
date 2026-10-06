package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 提交菜品问题反馈请求（{@code POST /dishes/{id}/correction}，dishId 在路径上）。
 * <p>
 * <p><b>请求体以 {@link #type} 判别</b>并附带 {@link #note} 选填补充（仅 {@code gone} 型可用）。
 * <p>
 * <b>type=field（信息有误）· 局部提交（patch）</b>：请求体<b>只含用户改动过的字段</b>——
 * 未传 = 保持原值不变；故下表字段<b>均为选填</b>，「校验（传入时）」一列表示该字段<b>一旦传入</b>必须满足的约束。
 * <b>无任何改动项 → 400「未提交任何改动」</b>。
 * <p>
 * <b>type=gone（已经下架）· 一键提交</b>：{@link #note} 与 {@link #images} <b>均为选填、允许全不传</b>
 *（提交即成立）；但<b>不接受任何差异项字段</b>（传入 → 400），因为它们表达的是「菜存在但写错了」，
 * 与「菜消失了」语义冲突。
 * <p>
 * 请求体无 {@code dishId}（{@code dishId} 在路径中）；描述属性经 {@code attributes} 动态提交。
 */
@Data
@Schema(description = "提交菜品问题反馈请求（type=field 局部提交改动项 / type=gone 一键提交）")
public class DishCorrectionReq {

    /**
     * 反馈类型：{@code field}（信息有误）/ {@code gone}（已经下架）。
     * <p>
     * <b>必填</b>（不做「不传即 field」的兜底 —— 两类的表单与校验完全不同，静默默认会让端上漏传时
     * 把「已经下架」当成「信息有误」提交，语义错位且难以发现）；非法值 → 400。
     */
    @NotBlank(message = "请选择问题类型")
    @Pattern(regexp = "field|gone", message = "问题类型不合法")
    @Schema(description = "反馈类型：field=信息有误 / gone=已经下架（**必填**）", example = "field", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "菜品名称（field 型传入时：非空、≤64 字）", example = "宫保鸡丁")
    private String name;

    @Schema(description = "现价（传入时：>0 的整数，单位分）", example = "1600")
    private Integer price;

    @Schema(description = "食堂名称（自由文本；传入时：非空、≤64 字）", example = "学一食堂")
    private String canteenName;

    @Schema(description = "档口名称（自由文本；传入时：非空、≤64 字）", example = "学一基本伙食")
    private String stallName;

    /**
     * 楼层（**归属档口**，非菜品）：楼层是 {@code stall.floor} 的属性，菜品无楼层字段。
     * <p>
     * 采纳时写回「目标档口」的 {@code stall.floor}——同档口下的其他菜品<b>一并生效</b>
     * （楼层是档口级描述，不是单菜属性）。**受控字典值、值即汉字**（`负一层` / `一层` /
     * `二层` / `三层` / `四层`，值域见 {@code canteen.constant.FloorDict}），传入即校验非空、∈ 字典、≤16 字
     * （与 {@code stall.floor VARCHAR(16)} 对齐）；字典外值 → {@code 400}。
     */
    @Schema(description = "楼层（受控字典值·值即汉字，归属档口 stall.floor；传入时：非空、∈ 楼层字典、≤16 字）",
            example = "二层")
    private String floor;

    /**
     * 动态描述属性（局部：仅含用户改动的维度项）。键 = 维度 {@code fieldKey}，值 = **中文文本**（或文本数组）；
     * 取值为自由文本、候选仅作提示不限制；命中内容安检则 400。
     */
    @Schema(description = "动态描述属性（键=维度 fieldKey，值=中文文本/数组；仅传改动维度）",
            example = "{\"dietType\":\"素\",\"flavorTags\":[\"辣\",\"酸\"]}")
    private Map<String, Object> attributes;

    /**
     * 菜品图片 URL 列表。
     * <p>
     * <b>type=field</b>：改动后的<b>完整</b>图片数组（经 {@code POST /upload/cloud-image} 转存的 COS 绝对地址），
     * <b>≤5 张</b>（纠错需多张佐证）；
     * <b>type=gone</b>：<b>选填补充</b>，<b>≤3 张</b>（用户路过拍一张当前窗口即可）。
     * 上限按 type 分派校验，见 {@code CorrectionServiceImpl}。
     */
    @Schema(description = "菜品图片 URL 列表（field 型：改动后的完整数组，≤5 张 / gone 型：选填补充，≤3 张；均可不传）")
    private List<String> images;

    /**
     * 补充说明（<b>仅 {@code type=gone} 的选填补充</b>，≤200 字；{@code type=field} 传入 → 400）。
     * <p>
     * 用途：让管理员在不跑现场的前提下判断「变成了别的菜 / 换窗口了 / 今天临时没供」——
     * 这三种情况若只靠「一键提交」会被压平成同一个信号，导致误下架。
     */
    @Schema(description = "补充说明（≤200 字；**仅 gone 型**可选填，field 型传入 → 400）",
            example = "这个窗口现在换成麻辣香锅了")
    private String note;
}
