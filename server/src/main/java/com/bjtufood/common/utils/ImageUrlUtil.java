package com.bjtufood.common.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class ImageUrlUtil {

    private final String publicBaseUrl;

    public ImageUrlUtil(@Value("${app.public-base-url:http://localhost:8080/api}") String publicBaseUrl) {
        this.publicBaseUrl = trimEnd(publicBaseUrl, "/");
    }

    public String toAbsoluteUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        // 微信云存储文件 ID（cloud://env-id.xxx/path）：小程序端直接使用，不拼接后端地址
        if (trimmed.startsWith("cloud://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return publicBaseUrl + trimmed;
        }
        return publicBaseUrl + "/" + trimmed;
    }

    public List<String> toAbsoluteUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return urls.stream()
                .filter(StringUtils::hasText)
                .map(this::toAbsoluteUrl)
                .toList();
    }

    public List<String> parseAndToAbsoluteUrls(String imagesJson) {
        return toAbsoluteUrls(JsonListUtil.parseStringList(imagesJson));
    }

    /**
     * 校验头像 URL 是否为受信任来源。
     * <p>
     * 允许的仅两类：
     * 1) 本站上传接口返回的站内相对路径（形如 {@code /images/...} 或 {@code /uploads/...}），
     *    防止将头像设为任意外部 URL（图片信标追踪 IP/UA、外链失联破图、诱导内容）；
     * 2) 微信云存储文件 ID（{@code cloud://...}），小程序端直接使用。
     * 其余 http/https 外部链接一律拒绝。
     */
    public boolean isValidAvatar(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("cloud://")) {
            return true;
        }
        // 站内相对路径：上传接口实际返回 /images/...（urlPrefix），同时兼容历史 /uploads/ 路径
        return trimmed.startsWith("/images/") || trimmed.startsWith("/uploads/");
    }

    /**
     * 校验 UGC 配图 URL 是否为受信任的 COS 绝对地址。
     * <p>
     * UGC 配图（评价/反馈）只允许走 {@code POST /upload/images} 链路产出：
     * fileID → 内容安全检测 → COS 转存，返回形如
     * {@code https://{bucket}.cos.{region}.myqcloud.com/{key}} 的绝对地址。
     * 校验规则：https 协议 + 腾讯云 COS 固定域名格式（.cos.{region}.myqcloud.com），
     * 拒绝外部图床/站内相对路径/云存储 fileID 混入，防止 UGC 配图沦为任意 URL 载体。
     *
     * @param url 待校验 URL
     * @return true=合法 COS 绝对地址
     */
    public boolean isValidCosUgcUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String trimmed = url.trim();
        // 域名正则：{bucket}.cos.{region}.myqcloud.com，bucket 可含 AppID 前缀（如 1250000000/bjtu-food 不出现在域名段，
        // 实际域名为 bjtu-food-1250000000.cos.ap-beijing.myqcloud.com），region 形如 ap-beijing
        return COS_URL_PATTERN.matcher(trimmed).matches();
    }

    /** COS 绝对地址匹配：https://{bucket}.cos.{region}.myqcloud.com/{key}，key 非空 */
    private static final java.util.regex.Pattern COS_URL_PATTERN = java.util.regex.Pattern.compile(
            "^https://[a-z0-9][a-z0-9-]*\\.cos\\.[a-z0-9-]+\\.myqcloud\\.com/\\S+$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static String trimEnd(String value, String suffix) {
        String result = value == null ? "" : value;
        while (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }
}
