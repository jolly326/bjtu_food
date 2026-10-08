package com.bjtufood.auth.service;

/**
 * 学生账号违规累积与梯度处置。
 *
 * <p><b>为什么需要它</b>：单条机审只能挡「明显违规」这一档 —— 它挡不住「反复试探边界」。
 * 内容层拦截之外，还须在**账号维度**累积：同一账号反复越线时逐级升级处置
 * （警告 → 限言 24 小时 → 限言 7 天 → 封禁）。
 *
 * <p><b>计数来源</b>：机审 {@code risky} 命中（内容被拦、未入库）+ 举报成立（管理端处置为「通过」）。
 *
 * <p><b>跨域位置</b>：本接口由 moderation 域（机审命中）与 feedback 域（举报成立）调用，
 * 是「内容域 → 账号域」的唯一写入口；账号侧字段与状态语义仍收口在 auth 域。
 */
public interface UserViolationService {

    /**
     * 记录一次机审违规命中（按 openid 定位账号）。
     *
     * <p>按 openid 而非 userId 定位，是因为机审调用点（{@code ContentSecurityService#checkText}）
     * 手上的唯一身份就是 openid，且**历史学号账号 openid 为 NULL** ⇒ 无法定位，直接跳过计数
     * （这类账号由举报成立路径覆盖）。
     *
     * @param openid 微信 openid（可空；空值不计数）
     */
    void recordModerationHit(String openid);

    /**
     * 记录一次「举报成立」——被举报内容的作者记一次违规。
     *
     * @param authorId 被举报内容作者的 userId（可空；空值不计数）
     */
    void recordReportUpheld(Long authorId);
}
