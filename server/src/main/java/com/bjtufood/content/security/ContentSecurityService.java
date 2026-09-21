package com.bjtufood.content.security;

/**
 * 微信内容安全检测服务（产品定稿 2026-09-13：全部 UGC 过微信内容安全检测）。
 * <p>
 * 覆盖三条链路：
 * <ul>
 *   <li>文本：msgSecCheck v2（scene=1 资料 / scene=2 评论），按 result.suggest 判定；</li>
 *   <li>图片：imgSecCheck（≤1MB，jpg/png），errcode=87014 判定违规；</li>
 *   <li>access_token：POST /cgi-bin/stable_token，缓存至过期前 ≥5 分钟，线程安全。</li>
 * </ul>
 * 调用方：ReviewServiceImpl（评价）、FeedbackServiceImpl（反馈）、AuthServiceImpl（昵称）、
 * UploadServiceImpl（云存储配图链路）。
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

    /**
     * 获取微信接口调用凭据（stable_token）。
     * <p>
     * POST /cgi-bin/stable_token（grant_type=client_credential）获取，
     * 进程内缓存至过期前 ≥5 分钟，线程安全；过期后由首个请求线程重新获取。
     *
     * @return 有效 access_token
     * @throws com.bjtufood.common.exception.BusinessException 未配置凭据=400；上游异常=500
     */
    String getStableAccessToken();

    /**
     * 清空进程内 access_token 缓存（token 失效自愈入口）。
     * <p>
     * 供使用 token 的其他出网点（如小程序云存储 batchdownloadfile）在收到
     * 40001（invalid credential）/ 42001（access_token expired）时调用：
     * 清空缓存后重新调 {@link #getStableAccessToken()} 即会重新拉取新 token，
     * 对齐 {@code ContentSecurityServiceImpl} 内部 BE-06 的「失效清缓存 + 重试一次」模式。
     */
    void invalidateCachedToken();

    /**
     * 微信凭据（appid/secret）是否已配置。
     * <p>
     * 未配置（本地开发/测试环境）时调用方应跳过内容安全检测放行；生产云托管必须配置
     * WECHAT_APPID / WECHAT_SECRET，否则内容安全检测整体失效（部署检查项，报告已备案）。
     */
    boolean isConfigured();
}
