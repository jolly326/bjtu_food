package com.bjtufood.common.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ImageUrlUtil {

    /**
     * COS 绝对地址匹配：{@code https://{bucket}.cos.{region}.myqcloud.com/{key}}，key 非空。
     * bucket 段含 AppID 后缀（如 {@code bjtu-food-1250000000}），region 形如 {@code ap-beijing}。
     */
    private static final Pattern COS_URL_PATTERN = Pattern.compile(
            "^https://[a-z0-9][a-z0-9-]*\\.cos\\.[a-z0-9-]+\\.myqcloud\\.com/\\S+$",
            Pattern.CASE_INSENSITIVE);

    private final String publicBaseUrl;

    public ImageUrlUtil(@Value("${app.public-base-url:http://localhost:8080/api/v1}") String publicBaseUrl) {
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

    /**
     * 绝对地址还原为站内相对路径（<b>入库口径</b>）。
     * <p>
     * {@code dish.images} / {@code banner.image_url} 是<b>相对路径列</b>，出参才由本类转绝对地址；
     * 若把绝对地址直接入库，出参会二次拼域名 ⇒ 详情页图片 404（如纠错采纳回写的 COS 地址）。
     * <p>
     * 规则：去掉 {@code app.public-base-url} 前缀即为相对路径；已是相对路径原样返回；
     * <b>非本站的绝对地址原样返回</b>（是否合法交由各业务的校验器判定，如 {@code isValidCosUgcUrl}）。
     */
    public String toRelativePath(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            String prefix = publicBaseUrl + "/";
            return trimmed.startsWith(prefix) ? trimmed.substring(publicBaseUrl.length()) : trimmed;
        }
        return trimmed;
    }

    /** 同 {@link #toRelativePath(String)}，批量版。 */
    public List<String> toRelativePaths(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return urls.stream()
                .filter(StringUtils::hasText)
                .map(this::toRelativePath)
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
     * 2) 走 {@code POST /upload/cloud-image} 链路产出的 COS 绝对地址（校验见 {@link #isValidCosUgcUrl}）——
     *    图片已过 {@code imgSecCheck} 内容安检并转存 COS。
     * <p>
     * 其余一律拒绝（含微信云存储 {@code cloud://} fileID：该形态绕过内容安检，不作为可入库的头像地址）。
     */
    public boolean isValidAvatar(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String trimmed = url.trim();
        // 站内相对路径：上传接口实际返回 /images/...（urlPrefix），同时兼容历史 /uploads/ 路径
        if (trimmed.startsWith("/images/") || trimmed.startsWith("/uploads/")) {
            return true;
        }
        return isValidCosUgcUrl(trimmed);
    }

    /**
     * 校验 UGC 配图 URL 是否为受信任的 COS 绝对地址。
     * <p>
     * UGC 配图（评价/反馈）只允许走 {@code POST /upload/cloud-image} 链路产出：
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
        return COS_URL_PATTERN.matcher(url.trim()).matches();
    }

    private static String trimEnd(String value, String suffix) {
        String result = value == null ? "" : value;
        while (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }
}
