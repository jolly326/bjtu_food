package com.bjtufood.wechat.config;

import com.bjtufood.wechat.constant.WechatApiConst;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 微信开放平台配置（类型化绑定，单一真源）。
 * <p>
 * <b>归属说明</b>：本类<b>不</b>放 {@code common.properties}，
 * 而随 {@code wechat} 域走。原因有二：
 * <ol>
 *   <li>默认端点值取自 {@link WechatApiConst}（微信平台常量），若置于 {@code common} 则形成
 *       {@code common → wechat} 依赖，直接违反 ArchTests 的「common 零业务依赖」不变式；</li>
 *   <li>本类只服务微信平台集成（{@code wechat} 域的 3 个消费者），按 package-by-feature 归位最贴切。</li>
 * </ol>
 * <p>
 * <b>单一真源</b>：{@code wechat.appid} / {@code wechat.secret} 统一由本类持有，
 * {@link #isConfigured()} 为「是否已配置」的唯一判据 —— 各消费者各自绑定会把判据分裂成多份实现。
 * <p>
 * 注册方式：启动类 {@code @EnableConfigurationProperties} 显式列举（见 {@code BjtuFoodApplication}；
 * 刻意**不用** {@code @ConfigurationPropertiesScan}，原因见该处注释——切片测试下扫描式注册不生效）。
 */
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /** 微信小程序 AppID（环境变量 {@code WECHAT_APPID} 注入） */
    private String appid = "";

    /** 微信小程序 Secret（环境变量 {@code WECHAT_SECRET} 注入） */
    private String secret = "";

    /**
     * 小程序登录凭证校验（jscode2Session）端点。
     * <p>
     * 默认值取自 {@link WechatApiConst#CODE2SESSION_URL}——与 {@code stable_token} /
     * {@code msg_sec_check} / {@code img_sec_check} / {@code batchdownloadfile} 同源，
     * 端点默认值只此一处（不出现「注解里硬编码 + 常量类」两种写法）。
     * 换环境（如内网代理）用环境变量 {@code WECHAT_CODE2SESSION_URL} 覆盖。
     */
    private String code2sessionUrl = WechatApiConst.CODE2SESSION_URL;

    /** 微信云开发环境 ID（{@code tcb/batchdownloadfile} 拉取云存储临时链接用） */
    private String cloudEnv = "";

    /**
     * 微信凭据是否已配置（<b>唯一判据</b>）。
     * <p>
     * 未配置（本地开发 / 测试环境）时，各调用方按既有产品口径<b>跳过</b>依赖微信凭据的检测放行；
     * 生产云托管必须注入 {@code WECHAT_APPID} / {@code WECHAT_SECRET}，否则内容安全检测与
     * 微信登录整体失效（部署检查项，报告已备案）。
     *
     * @return 已配置=true
     */
    public boolean isConfigured() {
        return StringUtils.hasText(appid) && StringUtils.hasText(secret);
    }

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getCode2sessionUrl() {
        return code2sessionUrl;
    }

    public void setCode2sessionUrl(String code2sessionUrl) {
        this.code2sessionUrl = code2sessionUrl;
    }

    public String getCloudEnv() {
        return cloudEnv;
    }

    public void setCloudEnv(String cloudEnv) {
        this.cloudEnv = cloudEnv;
    }
}
