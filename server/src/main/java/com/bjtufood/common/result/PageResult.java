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
 *     "total":   100       // 总记录数
 *   }
 * }
 * </pre>
 * <p>
 * <b>字段集</b>：分页壳恒为 {@code records} / {@code total} 两项；两端消费方
 * （client {@code recordsOf} / web {@code pageRecords}）均只读 {@code records}，
 * 故不输出任何零消费字段（页码 / 每页条数由请求侧自行掌握，回传即零消费冗余）。
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"records", "total"})
@Schema(description = "分页响应结果")
public class PageResult<T> {

    /** 当前页数据列表（契约字段） */
    @Schema(description = "当前页数据列表")
    private List<T> records;

    /** 总记录数 */
    @Schema(description = "总记录数", example = "100")
    private long total;

    /**
     * 创建分页结果（推荐入口）。
     *
     * @param records 当前页数据
     * @param total   总记录数
     */
    public static <T> PageResult<T> of(List<T> records, long total) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        return result;
    }

    /**
     * 分页结果转换（分页端点统一入口）。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getRecords(), page.getTotal());
    }
}
