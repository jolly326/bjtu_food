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
 *     "list":    [ ... ],  // 历史字段，过渡期保留，与 records 恒等值
 *     "total":   100,      // 总记录数
 *     "page":    1,        // 实际生效页码（经 PageUtil.normalize 归一化）
 *     "pageSize": 10       // 实际生效每页条数（经 PageUtil.normalize 归一化）
 *   }
 * }
 * </pre>
 * <p>
 * <b>兼容策略</b>：{@code list} 为过渡期保留的历史字段，其值由 {@link #getList()} 直接派生自
 * {@code records}（只读、无 setter），因此二者在序列化结果中<b>恒为同值</b>，不存在写入分叉的可能。
 * 待全部消费方切换到 {@code records} 后可移除 {@code list}。
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"records", "list", "total", "page", "pageSize"})
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
     * 历史字段：当前页数据列表。
     * <p>
     * 只读派生自 {@link #records}，保证与 records 恒等值；过渡期继续输出，不提供 setter。
     */
    @Schema(description = "数据列表（历史字段，等同于 records）")
    public List<T> getList() {
        return records;
    }

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
     * 创建分页结果（兼容重载，legacy）。
     * <p>
     * 保留以兼容历史调用方；因无法从入参获知归一化后的分页信息，page/pageSize 置 0。
     * 新代码请一律使用 {@link #of(List, long, int, int)}。
     *
     * @deprecated 请改用 {@link #of(List, long, int, int)} 以携带契约字段 page/pageSize
     */
    @Deprecated(since = "2026-09-14")
    public static <T> PageResult<T> of(List<T> records, long total) {
        return of(records, total, 0, 0);
    }
}
