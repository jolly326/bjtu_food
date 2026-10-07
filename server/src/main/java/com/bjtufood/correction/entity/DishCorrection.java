package com.bjtufood.correction.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bjtufood.common.persistence.StringListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品问题反馈实体类
 * <p>
 * 对应数据库表：dish_correction（菜品问题反馈，独立于 user_feedback 反馈体系）。
 * <p>
 * <b>按 {@link #type} 分两类</b>：
 * <ul>
 *   <li>{@code field}（信息有误）：提交的七字段为用户修正后的新值（<b>改动项快照</b>），
 *       管理端采纳后整体写回 dish（adopt 路径），拒绝则留存不采纳原因；</li>
 *   <li>{@code gone}（已经下架）：<b>一键提交即可成立</b> —— 各差异项列<b>恒 NULL</b>
 *       （语义是「无可对照的原值」，不是「未改动」），仅 {@link #note} 与 {@link #images}
 *       承载<b>选填</b>补充；管理端处置<b>只能下架</b>（{@code dish.status=off}，可逆），
 *       <b>绝不删除</b>（删除会级联清掉该菜全部评价）。</li>
 * </ul>
 * <p>
 * 例外：{@code floor}（楼层纠错）归属<b>档口</b>而非菜品——菜品无楼层字段，
 * 采纳时写回目标档口的 {@code stall.floor}（同档口其他菜品一并生效），详情出参的 {@code floor} 亦来自 stall 联表。
 */
@Data
@TableName(value = "dish_correction", autoResultMap = true)
@Schema(description = "菜品问题反馈")
public class DishCorrection {

    @TableId(type = IdType.AUTO)
    @Schema(description = "反馈ID")
    private Long id;

    /**
     * 反馈类型：field（信息有误，默认）/ gone（已经下架）。
     * <p>
     * 存量数据一律为 {@code field}（DB侧 {@code NOT NULL DEFAULT 'field'}）⇒ 升级后语义不变、无需回填。
     */
    @Schema(description = "反馈类型：field=信息有误 / gone=已经下架", example = "field")
    private String type;

    /** 目标菜品ID（逻辑关联 dish，无外键，与项目现状一致） */
    @Schema(description = "目标菜品ID")
    private Long dishId;

    /** 提交人用户ID（匿名提交为 0） */
    @Schema(description = "提交人用户ID（匿名提交为 0）")
    private Long userId;

    /** 提交的菜品名称 */
    @Schema(description = "提交的菜品名称", example = "宫保鸡丁")
    private String name;

    /** 提交的现价（单位：分） */
    @Schema(description = "提交的现价（分）", example = "1600")
    private Integer price;

    /** 提交的食堂名称（自由文本，无 GET /stalls 字典） */
    @Schema(description = "提交的食堂名称", example = "学一食堂")
    private String canteenName;

    /** 提交的档口名称（自由文本，采纳时两段式确认归档） */
    @Schema(description = "提交的档口名称", example = "学一基本伙食")
    private String stallName;

    /**
     * 提交的楼层（**改动项快照**，受控字典值·值即汉字，未改动留 NULL）。
     * <p>
     * 楼层归属档口：采纳时写回<b>目标档口</b>的 {@code stall.floor}，而不是 dish
     * （档口归属与 {@link #stallName} 一致，均经 canteen 域服务写契约下发）。
     * 长度上限与 {@code stall.floor VARCHAR(16)} 一致，值域见 {@code canteen.constant.FloorDict}，
     * 由 correction 侧提交时校验（字典外值 → 400）。
     */
    @Schema(description = "提交的楼层（受控字典值·值即汉字，采纳时写回目标档口 stall.floor）", example = "二层")
    private String floor;

    /**
     * 提交的描述属性（**改动项快照**：仅含用户改动的维度，未改动维度留 NULL）。
     * JSON 对象：键 = 维度 {@code id}（字符串形态），值 = <b>中文文本 / 数组</b>
     * （采纳时由服务端按「维度 + 中文」解析为取值 ID 后写回 {@code dish.attributes}）。
     */
    @Schema(description = "提交的描述属性（JSON：键=维度 ID，值=中文/数组；仅改动维度，可空）",
            example = "{\"1\":\"素\",\"3\":[\"辣\",\"酸\"]}")
    private String attributes;

    /**
     * 提交的菜品图片URL列表。
     * <p>
     * <b>type=field</b>：改动后的完整图片数组（≤ {@link com.bjtufood.correction.constant.CorrectionConst#IMAGE_MAX} 张）；
     * <b>type=gone</b>：<b>选填补充</b>（≤ {@link com.bjtufood.correction.constant.CorrectionConst#GONE_IMAGE_MAX} 张），
     * 用户只是路过拍一张当前窗口，用于表达「变成了别的菜 / 换窗口了」。
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    @Schema(description = "菜品图片URL列表（field 型=改动后的完整数组≤5张 / gone 型=选填补充≤3张，可空）")
    private List<String> images;

    /**
     * 补充说明（<b>仅 {@code type=gone} 的选填补充</b>，≤200 字；其余类型恒 NULL）。
     * <p>
     * 存在的理由：一键提交会把「变成了别的菜」/「换窗口了」/「今天临时没供」压平成同一个信号，
     * 而这三者对管理员是<b>完全不同</b>的处置判断。一句话说明即可让管理员免跑现场。
     * <p>
     * ⚠️ <b>不得设为必填</b> —— 一旦必填，用户成本从「点一下」回升到「填表」，提交量将大幅下降。
     */
    @Schema(description = "补充说明（≤200 字；仅 type=gone 的选填补充，其余 null）", example = "这个窗口现在换成麻辣香锅了")
    private String note;

    /** 处理状态：pending / adopted / rejected */
    @Schema(description = "处理状态：pending/adopted/rejected")
    private String status;

    /** 处理回复（采纳时固定「已采纳，菜品信息已更新」；拒绝时为管理员回复） */
    @Schema(description = "处理回复")
    private String reply;

    /** 不采纳原因（status=rejected 时必填，1~200 字） */
    @Schema(description = "不采纳原因（status=rejected 时非空）")
    private String rejectReason;

    /** 处理时间 */
    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间（DB 时钟：UPDATE 由 `ON UPDATE CURRENT_TIMESTAMP` 维护，应用层不写、不填充） */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
