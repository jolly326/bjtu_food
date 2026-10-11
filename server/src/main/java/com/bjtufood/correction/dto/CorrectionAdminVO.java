package com.bjtufood.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 菜品问题反馈管理端视图对象（{@code GET /admin/corrections}）。
 * <p>
 * 「处置状态 / 结论 / 处置文案 / 处置人 / 处置时间」来自处置任务表
 * （{@code moderation_task}，按 {@code type_id} 批量回填）—— 提交行本身只提供提交事实。
 */
@Data
@Schema(description = "菜品问题反馈管理端展示信息")
public class CorrectionAdminVO {

    @Schema(description = "反馈ID")
    private Long id;

    /**
     * 问题类型：{@code field}（信息有误）/ {@code gone}（已经下架）。
     * <p>
     * 管理端据此<b>分 Tab 展示与分派处置</b>：field → 差异对照 + 逐项采纳；gone → <b>仅下架</b>（无删除）。
     */
    @Schema(description = "问题类型：field=信息有误 / gone=已经下架", example = "field")
    private String type;

    @Schema(description = "目标菜品ID")
    private Long dishId;

    /**
     * 目标菜品名：联表回查 dish 实时补齐（菜品可能已被改名，<b>不区分上/下架</b>，含已下架）；
     * 菜品已被物理删除时为 null（前端按「菜品已删除」缺省展示）。
     */
    @Schema(description = "目标菜品名（实时回查 dish；菜品已物理删除为 null）")
    private String dishName;

    @Schema(description = "提交人用户ID（指向 user 表）")
    private Long userId;

    @Schema(description = "提交人昵称（账号已查无时为 null）")
    private String userName;

    @Schema(description = "提交的菜品名称")
    private String name;

    @Schema(description = "提交的现价（分）", example = "1600")
    private Integer price;

    @Schema(description = "提交的食堂名称")
    private String canteenName;

    @Schema(description = "提交的档口名称")
    private String stallName;

    /**
     * 提交的楼层（改动项快照，受控字典值·值即汉字；未改动为 null）。
     * 管理端据此判断本次纠错是否含楼层改动——有值时采纳会写回<b>目标档口</b>的 {@code stall.floor}。
     */
    @Schema(description = "提交的楼层（受控字典值·值即汉字，归属档口 stall.floor；未改动为 null）", example = "二层")
    private String floor;

    @Schema(description = "提交的描述属性（键=维度 ID，值=中文/数组；仅改动维度）",
            example = "{\"1\":\"素\",\"3\":[\"辣\",\"酸\"]}")
    private Map<String, Object> attributes;

    @Schema(description = "提交的菜品图片 URL 列表（field=改动后的完整数组≤3张 / gone=选填补充≤3张）")
    private List<String> images;

    /**
     * 补充说明（<b>仅 {@code type=gone} 的选填补充</b>，≤200 字；field 型恒 null）。
     * <p>
     * 管理端<b>处置gone 型时必须展示</b>：它承载「变成了别的菜 / 换窗口了 / 今天临时没供」，
     * 这三种情况的处置动作完全不同（补录 / 改档口 / 不下架），只看「已下架 N 人反馈」会误判。
     */
    @Schema(description = "补充说明（≤200 字；仅 type=gone 的选填补充，field 型为 null）",
            example = "这个窗口现在换成麻辣香锅了")
    private String note;

    @Schema(description = "处置状态：pending / handled")
    private String status;

    @Schema(description = "处置结论：handled=采纳/已下架 / rejected=不采纳；未处置为 null")
    private String outcome;

    @Schema(description = "处置回执短标题（未处置为 null）")
    private String replyTitle;

    @Schema(description = "处置文案 / 不采纳原因（未处置为 null）")
    private String replyContent;

    @Schema(description = "处置人登录名（未处置 / 账号已删除为 null）")
    private String handledByName;

    @Schema(description = "差异项数量（一眼看出「改了几项」；列表不必展开全部内容）")
    private Integer changeCount;

    @Schema(description = "含 floor 改动时的连带影响提示（同档口菜品数）")
    private String floorImpact;

    @Schema(description = "提交时间")
    private LocalDateTime createdAt;

    @Schema(description = "处置时间（未处置为 null）")
    private LocalDateTime handledAt;
}
