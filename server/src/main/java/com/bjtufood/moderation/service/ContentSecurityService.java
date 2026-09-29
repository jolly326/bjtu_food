package com.bjtufood.moderation.service;

import com.bjtufood.moderation.dto.SecSuggest;

/**
 * UGC 内容审核服务（微信 msgSecCheck v2 / imgSecCheck，<b>只管「内容是否合规」的判定</b>）。
 * <p>
 * 覆盖三条链路的审核判定：评价、反馈、昵称、以及上传配图。
 * <p>
 * 2026-09-28 架构收口 P1-A：自 {@code content.security} 迁至 {@code moderation.service}——
 * 「content」在本仓库语境中大量指代「UGC 内容」（字段 {@code content}、文案「内容不能为空」），
 * 作为包名极易与「内容管理（CMS）」混淆；本类的实际职责是<b>内容审核</b>，
 * {@code moderation} 才是精确命名。同时由 {@code content.security} 的
 * {@code security} 一层改为 {@code service}，与其余 10 个业务域的分层惯例一致。
 * <p>
 * 2026-09-28 架构收口 P0-B：{@code getStableAccessToken()} / {@code invalidateCachedToken()} /
 * {@code isConfigured()} 已<b>剥离</b>到 {@link com.bjtufood.wechat.service.WechatAccessTokenProvider}。
 * 剥离原因：这三者是<b>微信平台凭据与 token 生命周期</b>，与「内容是否违规」无关；
 * 混在一个接口里导致 {@code upload} 域为取 token 而依赖「内容安全服务」，
 * 甚至直接 import 实现类读取常量。剥离后本接口只保留审核判定，依赖方向归正。
 *
 * @see com.bjtufood.moderation.service.LocalSensitiveFilter 本地 DFA 词库过滤（审核链路的第一级）
 * @see com.bjtufood.wechat.service.WechatAccessTokenProvider 微信凭据获取（平台能力，已从本接口剥离）
 */
public interface ContentSecurityService {

    /**
     * 文本检测并统一拦截（UGC 主链路入口）。
     * <p>
     * 内部完成 risky 统一拦截：判定为 risky 时抛
     * {@code BusinessException(400, "内容包含违规信息，请修改后重试")}。
     * 返回值仅 PASS（放行）：内容安全检测 review（疑似）已归一为放行（2026-09-15 用户拍板取消人工复核），
     * 不再产生「待复核」语义与任何落库安全态。
     *
     * @param openid  当前用户的微信 openid（msgSecCheck v2 必填）；null 时跳过检测放行并返回 PASS
     *                （历史学号账号无 openid，属产品登记边界，报告已备案）
     * @param content 待检测文本（≤2500 字）
     * @param scene   场景值：1=资料（昵称） 2=评论（评价/反馈）
     * @return 恒为 PASS（放行）；risky 已统一拦截，永不返回
     * @throws com.bjtufood.common.exception.BusinessException risky=400；内容超长/上游异常=400 或 500
     */
    SecSuggest checkText(String openid, String content, int scene);

    /**
     * 文本检测原语（不拦截，返回放行/拒绝）。
     * <p>
     * 供测试与特殊场景使用；常规 UGC 链路请用 {@link #checkText}（含统一拦截）。
     * 内容安全检测 review（疑似）在此同样已归一为 PASS（放行），故实际返回 PASS / RISKY 二态。
     *
     * @param openid  微信 openid（null 时跳过检测返回 PASS）
     * @param content 待检测文本
     * @param scene   场景值：1=资料 2=评论
     * @return PASS（放行，含 review 归一）/ RISKY（拒绝）
     */
    SecSuggest detectText(String openid, String content, int scene);

    /**
     * 图片检测（imgSecCheck）。
     * <p>
     * 违规（errcode=87014）时统一抛
     * {@code BusinessException(400, "图片包含违规内容，无法上传")}。
     *
     * @param image 图片二进制（≤1MB，jpg/png；前端负责压缩，调用方负责大小兜底校验）
     * @throws com.bjtufood.common.exception.BusinessException 违规=400；上游异常=400/500
     */
    void checkImage(byte[] image);
}
