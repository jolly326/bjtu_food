package com.bjtufood.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 序列化配置。
 *
 * <p>统一日期格式与时区，确保 API 返回的日期字符串格式一致。
 *
 * <p><b>出参时间格式 = {@code yyyy-MM-dd HH:mm:ss}</b>（契约见
 * {@code docs/api/README.md} 的〈时间〉节）：DTO / VO 的时间字段一律是 {@link LocalDateTime}，
 * 而 {@code Jackson2ObjectMapperBuilder#dateFormat} <b>只作用于 {@code java.util.Date}</b>，
 * 对 JSR-310 类型无效 —— 只设它会让 {@code LocalDateTime} 按 ISO-8601 出参（带 {@code T} 分隔符），
 * 与契约漂移。故此处显式按类型登记 {@link LocalDateTime} 的序列化 / 反序列化格式。
 */
@Configuration
public class JacksonConfig {

    /** 全端统一的时间字符串格式（时区由下方 {@code timeZone} 固定为 Asia/Shanghai） */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        return new Jackson2ObjectMapperBuilder()
                // 中国时区
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                // 禁止将日期序列化为时间戳
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // JSR-310（LocalDateTime）：按契约格式出参 / 入参，不落 ISO-8601 的 'T' 分隔形态
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER))
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
