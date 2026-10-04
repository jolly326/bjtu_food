package com.bjtufood.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * <b>管理端</b>分页响应封装（{@code /admin/**} 专用）。
 * <p>
 * 与学生端 {@link PageResult} 的唯一差别 = <b>多一个 {@code total}</b>。原因是两端使用形态不同：
 * <ul>
 *   <li>学生端（C 端浏览）：只知���「还有没有下一页」，故只回 {@code records}；</li>
 *   <li>管理端（核对型使用）：需要「共 N 条」与总页数来做页码跳转与抽查，故回 {@code total}。</li>
 * </ul>
 * 契约见 docs/api/README.md「管理端分页结构 {@code AdminPageResult<T>}」与
 * docs/api/README.md 的通用约定（列表）。
 * <p>
 * <b>字段集</b>：{@code records}（当前页数据）+ {@code total}（满足条件的总条数）。
 * 请求侧仍持有 {@code page} / {@code pageSize}，管理端默认 {@code pageSize = 20}。
 *
 * @param <T> 列表项类型
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"records", "total"})
@Schema(description = "管理端分页响应结果（含总数）")
public class AdminPageResult<T> {

    /** 当前页数据列表 */
    @Schema(description = "当前页数据列表")
    private List<T> records;

    /** 满足查询条件的总条数（用于「共 N 条」与总页数 = ceil(total / pageSize)） */
    @Schema(description = "总条数")
    private long total;

    /**
     * 创建管理端分页结果（推荐入口）。
     *
     * @param records 当前页数据
     * @param total   总条数
     */
    public static <T> AdminPageResult<T> of(List<T> records, long total) {
        AdminPageResult<T> result = new AdminPageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        return result;
    }

    /**
     * 由 MyBatis-Plus 分页对象转换（管理端分页端点统一入口）。
     * <p>
     * {@code total} 取自分页对象的<b>总记录数</b>；若上游未执行 count（{@code searchCount=false}），
     * 退化为「当前页条数 + 满页判定」——调用方<b>不应</b>使用 {@code searchCount=false}。
     */
    public static <T> AdminPageResult<T> of(IPage<T> page) {
        long total = page.getTotal() > 0 ? page.getTotal() : page.getRecords().size();
        return of(page.getRecords(), total);
    }
}
