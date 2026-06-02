package com.bjtufood.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON 图片字段处理工具类
 * <p>
 * 数据库中 images 字段统一存 JSON 字符串，如：
 * ["/images/2026/05/a.jpg"]
 * <p>
 * 此工具类提供解析和序列化方法，兼容旧数据的逗号分隔格式。
 */
public class JsonListUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将 JSON 字符串解析为 List&lt;String&gt;
     * <p>
     * 解析规则：
     * 1. null 或空字符串返回空列表
     * 2. 如果是 JSON 数组，按 JSON 解析
     * 3. 如果不是 JSON，兼容逗号分隔字符串
     *
     * @param value JSON 字符串
     * @return 字符串列表
     */
    public static List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = value.trim();

        // 尝试按 JSON 数组解析
        if (trimmed.startsWith("[")) {
            try {
                return MAPPER.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                // 解析失败，回退到逗号分隔
            }
        }

        // 兼容旧数据：逗号分隔字符串（过滤空字符串、去除空格）
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 将 List&lt;String&gt; 序列化为 JSON 字符串
     *
     * @param list 字符串列表
     * @return JSON 字符串，null 输入返回 "[]"
     */
    public static String toJson(List<String> list) {
        if (list == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
