package com.bjtufood.correction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bjtufood.common.persistence.StringListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品问题反馈实体（用户提交的**事实**，append-only）。
 * <p>
 * 对应数据库表：`correction` —— 只存提交信息，**无状态列、无处置文案列**：
 * 「是否已处置 + 处置结论 + 处置文案」外置在 {@code moderation_task}
 * （多态键 {@code (type='correction', type_id)}）。
 * <p>
 * <b>按 {@link #type} 分两类</b>：
 * <ul>
 *   <li>{@code field}（信息有误）：提交的各字段为用户修正后的新值（<b>改动项快照</b>），
 *       使用时仅供管理员阅读对照；</li>
 *   <li>{@code gone}（已经下架）：<b>一键提交即可成立</b> —— 各差异项列<b>恒 NULL</b>
 *       （语义是「无可对照的原值」，不是「未改动」），仅 {@link #note} 与 {@link #images}
 *       承载<b>选填</b>补充；管理端处置<b>只能下架</b>（{@code dish.status=off}，可逆），
 *       <b>绝不删除</b>（删除会级联清掉该菜全部评价）。</li>
 * </ul>
 * <p>
 * 例外：{@code floor}（楼层纠错）归属<b>档口</b>而非菜品——本反馈流程不写实时楼层，目标档口的 {@code stall.floor} 改动由管理员在 [A2 档口管理](../A-主数据维护/A2-档口管理.md) 手动执行；详情出参的 {@code floor} 仅作对照值，不参与本处置流程。
 */
@Data
@TableName(value = "correction", autoResultMap = true)
@Schema(description = "菜品问题反馈")
public class Correction {

    @TableId(type = IdType.AUTO)
    @Schema(description = "反馈ID")
    private Long id;

    /** 反馈类型：field（信息有误）/ gone（已经下架） */
    @Schema(description = "反馈类型：field=信息有误 / gone=已经下架", example = "field")
    private String type;

    /** 目标菜品ID（逻辑关联 dish，无外键，与项目现状一致） */
    @Schema(description = "目标菜品ID")
    private Long dishId;

    /** 提交人用户ID（必填真实登录用户，外键 {@code fk_correction_user} → {@code user(id)}） */
    @Schema(description = "提交人用户ID（必填，指向 user 表）")
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

    /** 提交的档口名称（自由文本，不参与本处置流程） */
    @Schema(description = "提交的档口名称", example = "学一基本伙食")
    private String stallName;

    /**
     * 提交的楼层（**改动项快照**，受控字典值·值即汉字，未改动留 NULL）。
     * <p>
     * 楼层归属<b>档口</b>而非菜品：本反馈流程**不写实时楼层**；目标档口的 {@code stall.floor} 改动仅由管理员在 [A2 档口管理](../A-主数据维护/A2-档口管理.md) 手动执行。长度上限与 {@code stall.floor VARCHAR(16)} 一致，值域见 {@code canteen.constant.FloorDict}，由 correction 侧提交时校验（字典外值 → 400）。
     */
    @Schema(description = "提交的楼层（受控字典值·值即汉字，不写入本处置流程，仅作对照）", example = "二层")
    private String floor;

    /**
     * 提交的描述属性（**改动项快照**：仅含用户改动的维度，未改动维度留 NULL）。
     * <p>
     * 快照仅作留痕：本反馈流程**不写入** {@code dish.attributes}；取值 ID 解析与字典登记发生在 [A3 菜品管理](../A-主数据维护/A3-菜品管理.md) 编辑链路。
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
    @Schema(description = "菜品图片URL列表（field 型=改动后的完整数组≤3张 / gone 型=选填补充≤3张，可空）")
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

    /** 创建时间（DB 时钟：INSERT 由 `DEFAULT CURRENT_TIMESTAMP` 写入，应用层不写、不填充） */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}