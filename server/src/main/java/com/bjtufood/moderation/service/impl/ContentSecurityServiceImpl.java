package com.bjtufood.moderation.service.impl;

import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.moderation.dto.SecSuggest;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.wechat.constant.WechatApiConst;
import com.bjtufood.wechat.config.WechatProperties;
import com.bjtufood.wechat.service.WechatAccessTokenProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.function.Supplier;

/**
 * UGC 内容审核服务实现（msgSecCheck v2 / imgSecCheck）。
 * <p>
 * 判定口径（红线，固定如下）：
 * <ul>
 *   <li>文本以 {@code result.suggest} 判定，不得只看 errcode（errcode=0 仅代表调用成功）；
 *       用户拍板「取消人工复核」后归一为二态：pass/review → 放行，risky → 拒绝
 *       （归一发生在 {@link SecSuggest#fromValue} 判定入口，业务侧只需按 RISKY 判拒绝）；</li>
 *   <li>图片以 errcode 判定：0=通过，87014=违规，其余=调用失败 fail-closed；</li>
 *   <li>risky 统一在本服务拦截为 400「内容包含违规信息，请修改后重试」，文案不散落调用方；</li>
 *   <li>上游不可达/调用失败 fail-closed（500），保证入库内容必过审核（产品定稿「全部 UGC 过检」）；</li>
 *   <li>openid 为空（历史学号账号边界）跳过检测放行（报告已备案）。</li>
 * </ul>
 * <p>
 * 职责边界（架构收口 P0-B / P1-A）：
 * <ul>
 *   <li>stable_token 的获取 / 缓存 / 失效清理由 {@link WechatAccessTokenProvider}
 *       （微信平台凭据能力）承担，本类只消费其 {@code get()}；</li>
 *   <li>appid / secret 由凭据提供方持有并决定「是否已配置」，本类不持有；</li>
 *   <li>端点与 1MB 图片硬限制取自 {@link WechatApiConst}（平台常量，非本实现类的内部常量）。</li>
 * </ul>
 * BE-06 的「token 失效清缓存 + 重试一次」重试包装在本类（它包裹的是<b>审核调用整体</b>，
 * 而非 token 获取本身）；清缓存动作委托给 {@code tokenProvider.invalidate()}。
 */
@Slf4j
@Service
public class ContentSecurityServiceImpl implements ContentSecurityService {

    /** 连接/读取超时（毫秒）：与 WechatService 一致，防止微信接口挂起拖垮 UGC 主链路 */
    private static final int WECHAT_TIMEOUT_MS = 5000;

    /** msgSecCheck v2 内容长度上限（字） */
    private static final int MAX_TEXT_LENGTH = 2500;

    /** 微信响应固定以 text/plain 返回，统一先取 String 再手工反序列化（不依赖 Content-Type，P0 教训） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    /** 微信 access_token 凭据提供方（P0-B：凭据生命周期由它持有，本类只消费 {@code get()}） */
    private final WechatAccessTokenProvider tokenProvider;

    /** 微信出网基址与内网调用开关（云托管公网调用开放接口被 WAF 拦 412，切内网通道规避） */
    private final WechatProperties wechatProperties;

    /**
     * Spring 装配入口：注入凭据提供方，RestTemplate 走默认超时配置。
     * 保留单参构造的「可注入语义」，便于测试整体替换依赖。
     */
    @Autowired
    public ContentSecurityServiceImpl(WechatAccessTokenProvider tokenProvider, WechatProperties wechatProperties) {
        this(defaultRestTemplate(), tokenProvider, wechatProperties);
    }

    /**
     * 测试可注入构造：允许传入 MockRestServiceServer 绑定的 RestTemplate、受控凭据提供方与运行时基址；
     * 生产装配走 {@link #ContentSecurityServiceImpl(WechatAccessTokenProvider, WechatProperties)}。
     */
    public ContentSecurityServiceImpl(RestTemplate restTemplate, WechatAccessTokenProvider tokenProvider,
                                      WechatProperties wechatProperties) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.wechatProperties = wechatProperties;
    }

    /**
     * 内容安全专用 RestTemplate。
     * <p>
     * 🔴 <b>必须关闭输出流式（{@code setOutputStreaming(false)}）</b>：图片安检走 multipart 上传，
     * 流式输出会让 JDK {@code HttpURLConnection} 以 {@code Transfer-Encoding: chunked} 发送
     * （无 {@code Content-Length}），而<b>微信网关对 chunked 的 multipart 直接回
     * {@code 412 Precondition Failed}（空 body）</b> ⇒ 图片安检恒失败。关闭后请求体先缓冲、
     * 携带 {@code Content-Length}，微信正常受理（实测：带 Content-Length → 200，chunked → 412）。
     */
    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WECHAT_TIMEOUT_MS);
        factory.setReadTimeout(WECHAT_TIMEOUT_MS);
        factory.setOutputStreaming(false);
        return new RestTemplate(factory);
    }

    @Override
    public SecSuggest detectText(String openid, String content, int scene) {
        // 边界 1：微信凭据未配置（本地开发/测试环境）→ 跳过内容安全检测放行（生产必须配置，部署检查项）
        if (!tokenProvider.isConfigured()) {
            log.debug("微信内容安全检测未配置，跳过文本内容安全检测（scene={}）", scene);
            return SecSuggest.PASS;
        }
        // 边界 2：历史学号账号无 openid，msgSecCheck v2 无法调用 → 跳过内容安全检测放行（产品登记边界）
        if (!StringUtils.hasText(openid)) {
            log.debug("当前用户无 openid（历史学号账号），跳过文本内容安全检测（scene={}）", scene);
            return SecSuggest.PASS;
        }
        if (!StringUtils.hasText(content)) {
            // 空文本无可检内容，直接放行（纯图 UGC 场景）
            return SecSuggest.PASS;
        }
        if (content.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(400, "内容不能超过" + MAX_TEXT_LENGTH + "字");
        }

        Map<String, Object> reqBody = Map.of(
                "version", 2,
                "scene", scene,
                "openid", openid,
                "content", content);

        // BE-06：整段（取 token → 请求 → 判 errcode）纳入重试包装，
        // token 失效（40001/42001）时清空缓存重取一次，避免内容安全检测持续失败最长 2 小时。
        String url = wechatProperties.api(WechatApiConst.MSG_SEC_CHECK_URL);
        Map<String, Object> resp;
        if (wechatProperties.isInternalCall()) {
            // 云托管内网通道：平台自动注入鉴权，URL 不拼 access_token、无 token 重试面
            resp = doMsgSecCheck(url, reqBody, null);
        } else {
            resp = callWithTokenRetry(() -> doMsgSecCheck(url, reqBody, tokenProvider.get()), "msgSecCheck");
        }
        // 红线：以 result.suggest 判定，不只看 errcode；review 已在 SecSuggest.fromValue 归一为 PASS（放行）
        Object result = resp.get("result");
        String suggest = null;
        if (result instanceof Map<?, ?> resultMap) {
            Object raw = resultMap.get("suggest");
            suggest = raw == null ? null : String.valueOf(raw);
        }
        SecSuggest secSuggest = SecSuggest.fromValue(suggest);
        log.info("msgSecCheck 完成 scene={} suggest={} label={}", scene, suggest,
                result instanceof Map<?, ?> resultMap ? resultMap.get("label") : null);
        return secSuggest;
    }

    @Override
    public SecSuggest checkText(String openid, String content, int scene) {
        SecSuggest suggest = detectText(openid, content, scene);
        if (suggest == SecSuggest.RISKY) {
            throw new BusinessException(400, "内容包含违规信息，请修改后重试");
        }
        return suggest;
    }

    /**
     * token 失效自愈（BE-06）：首次失败若为 {@link TokenInvalidException}，
     * 先清空本地 token 缓存再重试一次（重试时 {@code tokenProvider.get()} 会重新拉取）。
     * 重试仍失败则按 fail-closed 口径抛 500，语义不变（不放行任何未过检内容）。
     *
     * @param action 单次微信调用（须把「取 token → 请求 → 判 errcode」整体包进来，重试才有意义）
     */
    private <T> T callWithTokenRetry(Supplier<T> action, String apiName) {
        try {
            return action.get();
        } catch (TokenInvalidException first) {
            log.warn("微信 access_token 失效（errcode={}），清空缓存后重试一次", first.errcode);
            tokenProvider.invalidate();
            try {
                return action.get();
            } catch (TokenInvalidException second) {
                log.error("刷新 access_token 后仍返回失效 errcode={}，fail-closed", second.errcode);
                throw secCheckUnavailable(apiName, second.errcode, "access_token 刷新后仍失效");
            }
        }
    }

    /**
     * token 失效信号（内部标记异常，由 {@link #callWithTokenRetry} 捕获并触发重取）。
     */
    private static final class TokenInvalidException extends RuntimeException {
        private final int errcode;

        TokenInvalidException(int errcode) {
            super("wechat token invalid: " + errcode);
            this.errcode = errcode;
        }
    }

    /** 微信 access_token 失效错误码（40001 invalid credential / 42001 timeout） */
    private static boolean isTokenInvalidErrcode(int errcode) {
        return errcode == 40001 || errcode == 42001;
    }

    /**
     * 内容安全接口失败的<b>归因提示</b>：把 errcode 直接编进返回给端上的文案。
     * <p>
     * <b>为何把 errcode 透出到端上</b>：
     * 若失败点一律抛「内容安全检测服务暂不可用，请稍后重试」，端上与排障都只能看到这一句话，
     * 必须翻服务端日志才知道是<b>白名单没配（40164）</b>、<b>AppSecret 错（40125）</b>
     * 还是<b>调用超限（48001）</b>。
     * 而 errcode 不含任何敏感信息（微信仅回传数字码与固定 errmsg），对非公网的小程序端透出
     * 无安全风险，却能让「看到提示」与「知道怎么修」之间<b>零日志往返</b>。
     * <p>
     * <b>归因映射</b>：
     * <ul>
     *   <li>{@code 40164} 出网 IP 未加白名单 → 最高频配置遗漏，直指操作</li>
     *   <li>{@code 40013}/{@code 40125} AppID/AppSecret 配错 → 指明是凭据而非内容</li>
     *   <li>{@code 41002} 缺 AppID → 指明环境变量缺失</li>
     *   <li>{@code 45011}/{@code 48001} 调用频率超限 → 与配置无关，纯限流</li>
     * </ul>
     * 其余 errcode 走默认分支，仍附 errcode 与接口名，保证任何新码都能被一眼定位。
     *
     * @param api     接口标识（{@code imgSecCheck} / {@code msgSecCheck}），用于日志与默认文案
     * @param errcode 微信返回的错误码（可能为 {@code null}，此时按未知处理）
     * @param errmsg  微信原始描述，仅进日志
     * @return 待抛出的业务异常（调用方直接 {@code throw}）
     */
    private BusinessException secCheckUnavailable(String api, Integer errcode, Object errmsg) {
        log.error("{} 调用失败 errcode={} errmsg={}", api, errcode, errmsg);
        int code = errcode == null ? -1 : errcode;
        return new BusinessException(500, switch (code) {
            case 40164 -> "内容安全检测不可用：微信后台未将本服务出口 IP 加入 IP 白名单（errcode=40164）";
            case 41002 -> "内容安全检测不可用：未配置微信 AppID（errcode=41002）";
            case 40013, 40125 ->
                    "内容安全检测不可用：微信 AppID 与 AppSecret 配置不匹配（errcode=" + code + "）";
            case 45011, 48001 ->
                    "内容安全检测调用超出频率限制（errcode=" + code + "），请稍后重试";
            default -> "内容安全检测不可用（" + api + " errcode=" + code + "），请稍后重试";
        });
    }

    @Override
    public void checkImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new BusinessException(400, "图片内容为空");
        }
        // 大小兜底（imgSecCheck 硬限制 1MB）；720×1334 尺寸限制由前端压缩保证，后端不做像素级校验
        if (image.length > WechatApiConst.MAX_IMAGE_SEC_CHECK_BYTES) {
            throw new BusinessException(400, "图片超过 1MB 限制，请压缩后重试");
        }
        if (!tokenProvider.isConfigured()) {
            log.debug("微信内容安全检测未配置，跳过图片内容安全检测");
            return;
        }
        // BE-06：整段（取 token → 请求 → 判 errcode）纳入重试包装，
        // token 失效（40001/42001）时清空缓存重取一次，避免图片内容安全检测持续失败最长 2 小时。
        if (wechatProperties.isInternalCall()) {
            // 云托管内网通道：URL 不拼 access_token（平台自动注入鉴权），无 token 重试面
            doImgSecCheck(image, null);
            return;
        }
        callWithTokenRetry(() -> {
            doImgSecCheck(image, tokenProvider.get());
            return null;
        }, "imgSecCheck");
    }

    private void doImgSecCheck(byte[] image, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("User-Agent", WechatApiConst.USER_AGENT);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 🔴 part 必须带 filename：微信 img_sec_check 要求 `media` 是「文件」项 —— 缺 filename
        //    （退化为普通表单字段）会回 errcode=47001「data format error」（实测）。文件名仅用于让
        //    Spring 写出 filename 与 part Content-Type；微信按字节嗅探实际类型（实测声明类型与实际
        //    内容不符亦受理）。同理不可省：请求体须带 Content-Length，见 defaultRestTemplate()。
        body.add("media", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "image.jpg";
            }
        });
        String respBody;
        try {
            String url = wechatProperties.api(WechatApiConst.IMG_SEC_CHECK_URL)
                    + (accessToken == null ? "" : "?access_token=" + accessToken);
            respBody = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        } catch (BusinessException e) {
            // token 获取失败等业务异常原样传播，不在此处二次包装
            throw e;
        } catch (ResourceAccessException e) {
            log.error("imgSecCheck 上游不可达", e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        } catch (Exception e) {
            log.error("imgSecCheck 调用失败", e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
        Map<String, Object> resp = parseJson(respBody, "img_sec_check");

        Integer errcode = asInt(resp.get("errcode"));
        if (errcode == null || errcode == 0) {
            return;
        }
        if (errcode == 87014) {
            // 违规图片统一拦截（非 token 问题，不触发重试）
            throw new BusinessException(400, "图片包含违规内容，无法上传");
        }
        if (isTokenInvalidErrcode(errcode)) {
            throw new TokenInvalidException(errcode);
        }
        // 其余 errcode（媒体格式不支持等）：fail-closed，交由用户重试或换图
        throw secCheckUnavailable("imgSecCheck", errcode, resp.get("errmsg"));
    }

    /** msgSecCheck 单次调用（accessToken 为 null = 内网通道，不拼 token 参数） */
    private Map<String, Object> doMsgSecCheck(String url, Map<String, Object> reqBody, String accessToken) {
        String body = postJson(accessToken == null ? url : url + "?access_token=" + accessToken, reqBody);
        Map<String, Object> r = parseJson(body, "msg_sec_check");
        Integer errcode = asInt(r.get("errcode"));
        if (errcode != null && errcode != 0) {
            if (isTokenInvalidErrcode(errcode)) {
                throw new TokenInvalidException(errcode);
            }
            // 其余 errcode：fail-closed 交由用户重试
            throw secCheckUnavailable("msgSecCheck", errcode, r.get("errmsg"));
        }
        return r;
    }

    // ==================== HTTP / 解析工具 ====================

    /** POST JSON（先按 String 读取再解析，微信响应 Content-Type 为 text/plain） */
    private String postJson(String url, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", WechatApiConst.USER_AGENT);
            return restTemplate.postForObject(url,
                    new HttpEntity<>(OBJECT_MAPPER.writeValueAsString(body), headers), String.class);
        } catch (ResourceAccessException e) {
            log.error("调用微信内容安全接口不可达（url={}）", maskUrl(url), e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信内容安全接口失败（url={}）", maskUrl(url), e);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
    }

    /** 反序列化微信响应体（非 JSON 视为网关异常，fail-closed 500） */
    private Map<String, Object> parseJson(String body, String api) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            });
            if (map == null) {
                throw new IllegalStateException("响应为空对象");
            }
            return map;
        } catch (Exception e) {
            log.error("{} 响应非 JSON：{}", api, body.length() > 200 ? body.substring(0, 200) : body);
            throw new BusinessException(500, "内容安全检测服务暂不可用，请稍后重试");
        }
    }

    /** errcode 双态安全解析（数字/字符串，参照 WechatService.parseErrcode） */
    private Integer asInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 日志脱敏：隐藏 access_token 等查询参数 */
    private String maskUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("(access_token=)[^&]*", "$1***");
    }
}
