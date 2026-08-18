package com.bjtufood.auth.service;

import com.bjtufood.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 微信小程序服务（spec §5.y.1）
 * <p>
 * 调用微信 jscode2session 接口，用 wx.login 的 code 换取 openid（+unionid 若有）。
 */
@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.code2session-url:https://api.weixin.qq.com/sns/jscode2session}")
    private String code2sessionUrl;

    /**
     * 登录凭证校验（code2Session）。
     *
     * @param code wx.login 临时凭证
     * @return 微信会话结果 { openid, unionid?, session_key }
     * @throws BusinessException code2Session 失败（未配置/接口错误/凭证无效）时 400
     */
    public WechatSession code2Session(String code) {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BusinessException(400, "微信登录未配置（WECHAT_APPID / WECHAT_SECRET 缺失）");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("appid", appid);
        params.add("secret", secret);
        params.add("js_code", code);
        params.add("grant_type", "authorization_code");

        String url = UriComponentsBuilder.fromHttpUrl(code2sessionUrl).queryParams(params).toUriString();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null) {
                throw new BusinessException(400, "微信登录校验失败：响应为空");
            }
            Integer errcode = resp.get("errcode") == null ? null : ((Number) resp.get("errcode")).intValue();
            if (errcode != null && errcode != 0) {
                log.warn("code2Session 失败 errcode={} errmsg={}", errcode, resp.get("errmsg"));
                throw new BusinessException(400, "微信登录凭证无效或已过期，请重试");
            }
            String openid = (String) resp.get("openid");
            if (openid == null || openid.isBlank()) {
                throw new BusinessException(400, "微信登录校验失败：未返回 openid");
            }
            String unionid = (String) resp.get("unionid");
            String sessionKey = (String) resp.get("session_key");
            return new WechatSession(openid, unionid, sessionKey);
        } catch (RestClientException e) {
            log.error("调用微信 code2Session 接口失败", e);
            throw new BusinessException(400, "微信登录服务异常，请稍后重试");
        }
    }

    /** 微信会话结果 */
    public record WechatSession(String openid, String unionid, String sessionKey) {
    }
}
