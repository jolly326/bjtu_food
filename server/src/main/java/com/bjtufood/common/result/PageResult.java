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
 * <b>字段集</b>：分页壳恒为 {@code records} / {@code total} / {@code page} / {@code pageSize} 四项；
 * 两端消费方（client {@code recordsOf} / web {@code pageRecords}）均只读 {@code records}，
 * 故不输出任何与 {@code records} 恒等派生的兼容字段（派生字段会让同一数组被 JSON 输出两次、响应体积翻倍）。
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

    /**
     * 分页结果转换（分页端点统一入口）。
     * <p>
     * {@code page} / {@code pageSize} 取 {@code IPage} 的 current/size —— 二者由 Service 内
     * {@code PageUtil.normalize} 归一化，即「实际生效值」；直接取 Controller 原始入参会
     * 把越界 / 超限的入参写进响应，端上无从得知服务端真正用了什么。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
