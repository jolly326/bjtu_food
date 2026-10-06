package com.bjtufood.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 * <p>
 * 功能：将 /images/** 路径映射到本地文件系统，使上传的图片可通过 URL 直接访问
 * <p>
 * 使用方式：
 * - 数据库存储图片路径：/images/2026/05/xxx.jpg
 * - 对外访问地址由 app.public-base-url 拼接生成
 * <p>
 * 存储根目录与 URL 前缀取自 {@link UploadProperties}，与 {@code UploadServiceImpl} <b>共用同一份配置</b> ——
 * 两处各写一份 {@code @Value} 会在改一处忘另一处时出现「图片存得进去、访问不到」（或反之），
 * 且只在真机上传后才暴露。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    public WebMvcConfig(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /images/** URL 映射到本地 upload.path 目录
        Path path = Paths.get(uploadProperties.getPath()).toAbsolutePath().normalize();
        registry.addResourceHandler(uploadProperties.resourcePattern())
                .addResourceLocations(path.toUri().toString());
    }
}
