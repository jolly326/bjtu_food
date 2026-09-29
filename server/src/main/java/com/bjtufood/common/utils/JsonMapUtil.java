package com.bjtufood.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 对象（{@code Map<String, Object>}）列处理工具类
 * <p>
 * 用于菜品描述属性 {@code dish.attributes} 一类的 JSON 对象列：
 * 键 = 维度 {@code fieldKey}，值 = 机器值（{@code single} 为字符串 / {@code multi} 为字符串数组）。
 * 「列 ↔ Map」的转换集中在此，避免各域各写一份 ObjectMapper。
 */
public class JsonMapUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonMapUtil() {
    }

    /**
     * 将 JSON 对象串解析为 {@code Map<String, Object>}（保持键的声明顺序，便于按维度顺序装配）。
     *
     * @param value JSON 串；null / 空白 / 解析失败返回空 Map
     * @return 键值映射（值形态：String 或 List&lt;String&gt;）
     */
    public static Map<String, Object> parseObject(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 将 {@code Map<String, Object>} 序列化为 JSON 对象串。
     *
     * @param map 键值映射；null 或空 Map 返回 null（列语义为「无属性」）
     * @return JSON 串
     */
    public static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
}
