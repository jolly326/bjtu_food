package com.bjtufood.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * 审计的「变更前后值」快照载体（请求级 ThreadLocal）。
 *
 * <p><b>解决什么</b>：只记「谁在什么时间调了哪个端点」不足以回答**误删后如何重建** ——
 * 需要知道那条记录**原来长什么样**。快照就是为这一件事存在的。
 *
 * <p><b>为什么用 ThreadLocal 而不是把参数一路传下去</b>：审计写在过滤器里（请求结束的
 * {@code finally}），而对象状态只有在 Service 内部才拿得到。让每个 Service 方法多一个
 * 「审计出参」是侵入式的；ThreadLocal 让「谁有数据谁登记」成为局部动作。
 *
 * <p><b>生命周期</b>：仅在一次 HTTP 请求的线程内有效 —— 过滤器在请求结束时
 * {@link #take()} 取走并清除，读请求走 {@link #clear()}。
 * <b>绝不允许跨请求残留</b>（容器线程是复用的）。
 *
 * <p><b>只记对象现状，不记请求体</b>：请求体可能含口令类字段（如改密），一律不入审计。
 */
@Slf4j
public final class AuditSnapshot {

    /** 单侧快照的长度上限（列类型为 TEXT，留足余量并防超长行） */
    private static final int MAX_LENGTH = 8_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final ThreadLocal<String[]> HOLDER = new ThreadLocal<>();

    private AuditSnapshot() {
    }

    /**
     * 记录对象「变更前」的形态。
     *
     * @param state 变更前的对象（实体 / DTO 均可）；{@code null} 表示无此侧快照
     */
    public static void before(Object state) {
        slot()[0] = toJson(state);
    }

    /**
     * 记录对象「变更后」的形态（仅状态跃迁类写操作需要；删除类无此侧）。
     *
     * @param state 变更后的对象；{@code null} 表示无此侧快照
     */
    public static void after(Object state) {
        slot()[1] = toJson(state);
    }

    /**
     * 取走本次请求的快照并清除（**必须**在请求结束时调用）。
     *
     * @return 长度 2 的数组：{@code [before, after]}；无快照时两侧均为 {@code null}
     */
    public static String[] take() {
        String[] values = HOLDER.get();
        HOLDER.remove();
        return values == null ? new String[]{null, null} : values;
    }

    /** 清除本次请求的快照（非审计请求在请求结束时调用，防跨请求残留） */
    public static void clear() {
        HOLDER.remove();
    }

    private static String[] slot() {
        String[] values = HOLDER.get();
        if (values == null) {
            values = new String[2];
            HOLDER.set(values);
        }
        return values;
    }

    /**
     * 序列化为 JSON 文本。
     *
     * <p>序列化失败**不抛出**：审计是旁路，不能因为「某个对象序列化不了」把业务请求打成 500；
     * 失败时记 ERROR 并返回 {@code null}（快照缺失，留痕仍在）。
     */
    private static String toJson(Object state) {
        if (state == null) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(state);
            return json.length() <= MAX_LENGTH ? json : json.substring(0, MAX_LENGTH);
        } catch (Exception e) {
            log.error("[AUDIT] 变更快照序列化失败：{}", e.getClass().getSimpleName());
            return null;
        }
    }
}
