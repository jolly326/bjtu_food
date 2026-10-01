package com.bjtufood.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
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
                // null 字段不下发（P1）。收益已被基线量化（docs/perf/perf-00 §3）：详情 −2.8%、
                // 列表页 −4.2%——**收益不大，所以这里的理由是「小程序按量计费，出参体积就是成本」，
                // 而不是「序列化性能优化」**。成立前提：端上对可空字段一律走兜底
                // （|| ''、!= null、Array.isArray），不依赖「字段存在但为 null」与「字段缺失」的区分；
                // 契约镜像里这些字段本就标 nullable，省略它们仍是 schema 合法出参。
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // 注册 Java 8 时间类型支持
                .modules(new JavaTimeModule());
    }
}
