package com.bjtufood.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 功能：将 /uploads/** 路径映射到本地文件系统，使上传的图片可通过 URL 直接访问
 * <p>
 * 使用方式：
 * - 上传图片后返回路径：/uploads/2024/01/abc123.jpg
 * - 前端展示：<image src="http://localhost:8080/api/uploads/2024/01/abc123.jpg" />
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** URL 映射到本地 uploads 目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
