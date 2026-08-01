package com.bjtufood.common.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Jackson 序列化配置
 * <p>
 * 功能：统一日期格式、时区处理，确保 API 返回的日期字符串格式一致
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        return new Jackson2ObjectMapperBuilder()
                // 日期格式：yyyy-MM-dd HH:mm:ss
                .dateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                // 中国时区
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                // 禁止将日期序列化为时间戳
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 注册 Java 8 时间类型支持
                .modules(new JavaTimeModule());
    }
}
