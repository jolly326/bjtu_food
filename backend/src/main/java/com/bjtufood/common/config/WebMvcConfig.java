package com.bjtufood.common.config;

import org.springframework.beans.factory.annotation.Value;
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
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.path:./uploads/images}")
    private String uploadPath;

    @Value("${upload.url-prefix:/images}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /images/** URL 映射到本地 upload.path 目录
        String pattern = urlPrefix.endsWith("/**") ? urlPrefix : urlPrefix + "/**";
        Path path = Paths.get(uploadPath).toAbsolutePath().normalize();
        registry.addResourceHandler(pattern)
                .addResourceLocations(path.toUri().toString());
    }
}
