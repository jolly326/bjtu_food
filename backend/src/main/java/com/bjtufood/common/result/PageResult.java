package com.bjtufood.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应封装
 * <p>
 * 列表接口统一使用此类返回分页数据，格式：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "成功",
 *   "data": {
 *     "list": [ ... ],    // 当前页数据
 *     "total": 100        // 总记录数
 *   }
 * }
 * </pre>
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应结果")
public class PageResult<T> {

    /** 当前页数据列表 */
    @Schema(description = "数据列表")
    private List<T> list;

    /** 总记录数 */
    @Schema(description = "总记录数", example = "100")
    private long total;

    /** 创建分页结果 */
    public static <T> PageResult<T> of(List<T> list, long total) {
        return new PageResult<>(list, total);
    }
}
