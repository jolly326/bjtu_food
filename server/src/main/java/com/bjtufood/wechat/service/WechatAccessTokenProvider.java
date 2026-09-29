package com.bjtufood.wechat.service;

/**
 * 微信 access_token 获取与缓存（<b>平台凭据能力</b>，非内容安全判定）。
 * <p>
 * 2026-09-28 架构收口 P0-B（从 {@code ContentSecurityService} 拆出）：
 * 原接口名为「内容安全服务」却同时暴露了 {@code getStableAccessToken()} /
 * {@code invalidateCachedToken()} / {@code isConfigured()} —— 这三个是<b>微信平台凭据与
 * token 生命周期管理</b>，与「内容是否违规」的判定是两件事。后果是
 * {@code upload} 域为拿 token 而依赖「内容安全服务」（{@code upload -> content}），
 * 甚至直接 import 实现类（见 {@code UploadServiceImpl} 旧第 6 行）。
 * <p>
 * 拆出后：内容判定见 {@code moderation.service.ContentSecurityService}，
 * 凭据获取见本接口；{@code moderation} 与 {@code upload} 均只依赖<b>接口</b>，
 * 二者之间无横向依赖。
 *
 * @see com.bjtufood.wechat.service.impl.WechatAccessTokenProviderImpl
 */
public interface WechatAccessTokenProvider {

    /**
     * 获取可用的 access_token（进程内缓存，有效期内复用）。
     * <p>
     * 缓存到期余量：过期前 5 分钟即视为失效并重新拉取（避免边界失效）。
     * 并发下采用双重检查 + 同步，缓存失效时只由首个线程真正拉取。
     *
     * @return 有效 access_token
     * @throws com.bjtufood.common.exception.BusinessException 未配置凭据=400；上游不可达/返回空=500
     */
    String get();

    /**
     * 清空进程内 access_token 缓存（token 失效自愈入口）。
     * <p>
     * 供各出网点在收到 40001（invalid credential）/ 42001（access_token expired）时调用：
     * 清空后重新 {@link #get()} 即会重新拉取。
     */
    void invalidate();

    /**
     * 微信凭据（appid / secret）是否已配置。
     * <p>
     * 未配置（本地开发 / 测试环境）时调用方应<b>跳过</b>依赖微信凭据的检测放行；
     * 生产云托管必须配置 {@code WECHAT_APPID} / {@code WECHAT_SECRET}，否则内容安全检测整体失效
     * （部署检查项，报告已备案）。
     *
     * @return 已配置=true
     */
    boolean isConfigured();
}
