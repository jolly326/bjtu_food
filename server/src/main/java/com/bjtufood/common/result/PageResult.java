package com.bjtufood.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应封装
 * <p>
 * 列表接口统一使用此类返回分页数据，契约字段（见 docs/api/README.md「通用结构」）：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "成功",
 *   "data": {
 *     "records": [ ... ]   // 当前页数据（分页壳唯一字段）
 *   }
 * }
 * </pre>
 * <p>
 * <b>字段集</b>：分页壳<b>恒为 {@code records} 一项</b>；结束判据 = 本页返回条数 &lt; 请求的 {@code pageSize}
 * ——页码 / 每页条数由请求侧掌握、不回传，总数亦不下发（其唯一用途是算「还有没有下一页」，
 * 而该语义已由「本页条数 &lt; pageSize」完整表达，下发即零消费冗余）。
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"records"})
@Schema(description = "分页响应结果")
public class PageResult<T> {

    /** 当前页数据列表（分页壳唯一字段） */
    @Schema(description = "当前页数据列表")
    private List<T> records;

    /**
     * 创建分页结果（推荐入口）。
     *
     * @param records 当前页数据
     */
    public static <T> PageResult<T> of(List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        return result;
    }

    /**
     * 分页结果转换（分页端点统一入口）。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getRecords());
    }
}
