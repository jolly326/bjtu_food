package com.bjtufood.common.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应封装
 * <p>
 * 列表接口统一使用此类返回分页数据，契约字段（见 docs/project_spec.md §5）：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "成功",
 *   "data": {
 *     "records": [ ... ],  // 当前页数据（契约字段）
 *     "total":   100,      // 总记录数
 *     "page":    1,        // 实际生效页码（经 PageUtil.normalize 归一化）
 *     "pageSize": 10       // 实际生效每页条数（经 PageUtil.normalize 归一化）
 *   }
 * }
 * </pre>
 * <p>
 * <b>字段集</b>：分页壳恒为 {@code records} / {@code total} / {@code page} / {@code pageSize} 四项。
 * 原过渡期字段 {@code list} 已于 2026-09-21 删除（见 docs/project_spec.md §7.33）：两端消费方
 * （client {@code recordsOf} / web {@code pageRecords}）均只读 {@code records}，而 {@code list}
 * 派生自 {@code records} 并参与序列化会让同一数组被 JSON 输出两次、列表响应体积≈翻倍。
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"records", "total", "page", "pageSize"})
@Schema(description = "分页响应结果")
public class PageResult<T> {

    /** 当前页数据列表（契约字段） */
    @Schema(description = "当前页数据列表")
    private List<T> records;

    /** 总记录数 */
    @Schema(description = "总记录数", example = "100")
    private long total;

    /** 实际生效页码（归一化后） */
    @Schema(description = "当前页码", example = "1")
    private int page;

    /** 实际生效每页条数（归一化后） */
    @Schema(description = "每页条数", example = "10")
    private int pageSize;

    /**
     * 创建分页结果（推荐入口）。
     * <p>
     * 统一在此填充 records / total / page / pageSize，{@code page} 与 {@code pageSize}
     * 须传入经 {@code PageUtil.normalize} 归一化后的实际生效值，避免各调用点分散处理。
     *
     * @param records  当前页数据
     * @param total    总记录数
     * @param page     实际生效页码（归一化后）
     * @param pageSize 实际生效每页条数（归一化后）
     */
    public static <T> PageResult<T> of(List<T> records, long total, int page, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }
}
