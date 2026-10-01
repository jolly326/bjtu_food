package com.bjtufood.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 对象（{@code Map<String, Object>}）列处理工具类
 * <p>
 * 用于菜品描述属性 {@code dish.attributes} 一类的 JSON 对象列：
 * 键 = 维度 {@code fieldKey}，值 = 机器值（{@code single} 为字符串 / {@code multi} 为字符串数组）。
 * 「列 ↔ Map」的转换集中在此，避免各域各写一份 ObjectMapper。
 * <p>
 * <b>失败口径</b>：解析/序列化失败一律<b>回落</b>（空 Map / null）而不是抛异常——列值脏一点不该
 * 让整个读接口 500。但回落<b>必须留下 WARN</b>：此前这两处 catch 完全静默，脏 JSON 会悄悄变成
 * 「该菜品没有属性」，既不报错也无痕迹，是最难查的一类数据问题。
 * 日志刻意只打原因、不打堆栈：批量脏数据时（如一次全库扫描）堆栈会淹没日志，而这里需要的是
 * 「有哪些行脏了」的可检索线索，不是回溯。
 */
@Slf4j
public class JsonMapUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 日志里对长文本的截断长度（避免把整列 JSON 灌进日志） */
    private static final int LOG_BRIEF_LENGTH = 200;

    private JsonMapUtil() {
    }

    /**
     * 将 JSON 对象串解析为 {@code Map<String, Object>}（保持键的声明顺序，便于按维度顺序装配）。
     *
     * @param value JSON 串；null / 空白 / 解析失败返回空 Map（解析失败另记 WARN）
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
            log.warn("属性 JSON 解析失败，已回落为空 Map（列值前 {} 字符：{}；原因：{}）",
                    LOG_BRIEF_LENGTH, brief(value), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 将 {@code Map<String, Object>} 序列化为 JSON 对象串。
     *
     * @param map 键值映射；null 或空 Map 返回 null（列语义为「无属性」）
     * @return JSON 串；序列化失败返回 null（另记 WARN）
     */
    public static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("属性 Map 序列化失败，已回落为 null（列语义=无属性；原因：{}）", e.getMessage());
            return null;
        }
    }

    /** 日志用短文本：过长则截断并加省略号 */
    private static String brief(String value) {
        return value.length() <= LOG_BRIEF_LENGTH ? value : value.substring(0, LOG_BRIEF_LENGTH) + "…";
    }
}
