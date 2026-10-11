package com.bjtufood.correction.constant;

import java.util.Set;

/**
 * 菜品问题反馈相关常量（类型值域 / 字段约束 / 缺省回执文案单一真源）。
 * <p>
 * 按 {@link #TYPE_FIELD} / {@link #TYPE_GONE} 两类分派：信息有误走「改动项快照 + 逐项采纳」，
 * 已经下架走「一键提交 + 仅下架」。
 * <p>
 * <b>处置状态与结论不在此</b>：`correction` 表只存提交事实，「是否已处置 + 处置文案」
 * 外置在 `moderation_task`，值域见 {@code com.bjtufood.moderation.constant.ModerationConst}。
 */
public interface CorrectionConst {

    // ==================== type（反馈类型） ====================

    /** 反馈类型：信息有误 —— 局部提交「改动项快照」（各字段中仅改动项有值） */
    String TYPE_FIELD = "field";
    /** 反馈类型：已经下架 —— 一键提交即可成立，各差异项列恒 NULL，仅用 note/images 承载选填补充 */
    String TYPE_GONE = "gone";

    /** 反馈类型白名单（入参校验用；type 必填、非法值 400） */
    Set<String> QUERY_TYPES = Set.of(TYPE_FIELD, TYPE_GONE);

    /** {@code type=gone} 补充说明最大长度（字，与 `correction.note` 列宽一致） */
    int NOTE_MAX_LENGTH = 200;

    /**
     * {@code type=gone} 配图上限（张）。
     * <p>
     * 与 {@link #IMAGE_MAX} 同为 3 张：gone 是「可选补充」，field 是「改动项佐证」，
     * 但两者都属 UGC 配图，**统一按全站 UGC 上限 3 张**执行（见
     * {@code com.bjtufood.upload.support.UgcImageValidator#MAX_IMAGES}），不按 type 分档。
     */
    int GONE_IMAGE_MAX = 3;

    // ==================== 缺省回执文案（工单属主域提供，避免 moderation 域猜业务措辞） ====================

    /** gone 型采纳（下架）缺省回执文案 */
    String GONE_ADOPT_REPLY = "已下架，感谢反馈";

    /** gone 型驳回回执短标题（语义为「经核实仍在售」） */
    String GONE_REJECT_TITLE = "菜品仍在售";

    /** gone 型采纳回执短标题 */
    String GONE_ADOPT_TITLE = "菜品已下架";

    /** field 型采纳（全部项）缺省回执文案 */
    String ADOPT_REPLY = "已采纳，菜品信息已更新";

    // ==================== 字段约束 ====================

    /** 提交的菜品名称最大长度（字，与 `dish.name` 列宽一致） */
    int NAME_MAX_LENGTH = 64;

    /** 提交的食堂名称最大长度（字，与 `canteen.name` 列宽一致） */
    int CANTEEN_NAME_MAX_LENGTH = 64;

    /** 提交的档口名称最大长度（字，与 `stall.name` 列宽一致） */
    int STALL_NAME_MAX_LENGTH = 64;

    /**
     * 提交的楼层最大长度（字，与 {@code stall.floor} / {@code correction.floor} 的 VARCHAR(16) 一致）。
     * <p>
     * 楼层是<b>受控字典值（值即汉字）</b>——值域见 {@code canteen.constant.FloorDict}
     * （`负一层` / `一层` / `二层` / `三层` / `四层`），字典外值 → {@code 400}；
     * 本长度上限为列宽兜底：纠错采纳会把提交值<b>原样写回 stall.floor</b>，
     * 两者上限不一致会在采纳时触发截断或 500（数据被静默改写）。
     */
    int FLOOR_MAX_LENGTH = 16;

    /**
     * 纠错（{@code type=field}）配图上限（张）= 3。
     * <p>
     * 与评价 / 反馈 / gone 型**同档**：UGC 配图统一按全站上限 3 张执行（见
     * {@code com.bjtufood.upload.support.UgcImageValidator#MAX_IMAGES}）——
     * 「同一种图片行为、不同上限」只会让口径难以解释，3 张已足够表达「菜品 + 价签 + 档口牌」。
     */
    int IMAGE_MAX = 3;
}
