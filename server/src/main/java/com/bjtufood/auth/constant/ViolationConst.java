package com.bjtufood.auth.constant;

/**
 * 学生账号违规与处置的字面量（来源 / 处置动作）。
 *
 * <p>与 {@link UserConst} 分开：状态是账号的「当前形态」，违规记录是「发生过什么」，
 * 二者的取值域不应混在一处，否则会误以为 {@code mute_24h} 也是 {@code status} 的合法值。
 */
public interface ViolationConst {

    /** 来源：机审 risky 命中（内容未入库） */
    String SOURCE_MODERATION = "moderation";

    /** 来源：举报成立（管理端处置结论为 handled） */
    String SOURCE_REPORT = "report";

    /** 处置：仅累计，未达处置阈值 */
    String ACTION_COUNTED = "counted";

    /** 处置：账号标记 + 警告 */
    String ACTION_WARN = "warn";

    /** 处置：限言 24 小时 */
    String ACTION_MUTE_24H = "mute_24h";

    /** 处置：限言 7 天 */
    String ACTION_MUTE_7D = "mute_7d";

    /** 处置：封禁（等同账号禁用：拦登录 + 吊销既有 token） */
    String ACTION_BAN = "ban";
}
