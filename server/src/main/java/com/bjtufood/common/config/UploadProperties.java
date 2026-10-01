package com.bjtufood.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地图片存储配置（类型化绑定，<b>单一真源</b>）。
 * <p>
 * <b>为什么要有这个类</b>：{@code upload.path} / {@code upload.url-prefix} 此前被
 * {@code WebMvcConfig}（映射 {@code /images/**} → 本地目录）与 {@code UploadServiceImpl}
 * （落盘目录 + 返回 URL 前缀）<b>各自 {@code @Value} 绑定一次，连默认值字面也各写一份</b>。
 * 也就是说「同一个目录」有两处真源：改一处忘另一处，图片就会「存得进去、访问不到」
 * （或者反过来），且现象只在真机上传后才暴露。现收敛为一份配置。
 * <p>
 * 与仓内「架构收口 P2：{@code @Value} → 类型化 Properties」的既定方向一致。
 */
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /** 上传文件存储根目录（相对项目根目录；由环境变量 {@code UPLOAD_PATH} 注入） */
    private String path = "./uploads/images";

    /** 对外访问前缀（由环境变量 {@code UPLOAD_URL_PREFIX} 注入） */
    private String urlPrefix = "/images";

    /**
     * 静态资源处理器 pattern：{@code /images} → {@code /images/**}；已显式带 {@code /**} 则原样返回。
     * <p>
     * 收敛进来的理由同上：此前该拼装逻辑内联在 {@code WebMvcConfig} 里，
     * 属于「前缀的第二种写法」，容易与上传侧返回的 URL 前缀不一致。
     */
    public String resourcePattern() {
        return urlPrefix.endsWith("/**") ? urlPrefix : urlPrefix + "/**";
    }

    /** 去掉尾部斜杠的前缀，供拼接「前缀 + /yyyy/MM/dd/xxx.jpg」用 */
    public String urlPrefixWithoutTrailingSlash() {
        return urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }
}
