package com.bjtufood.common.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.function.Function;

/**
 * 分页参数归一化工具
 * <p>
 * 统一兜底：page 最小为 1，pageSize 上限 100、最小为 1。
 * 防止前端传入过大的 pageSize 造成数据库全表扫描 / 内存压力。
 */
public final class PageUtil {

    /** pageSize 硬上限 */
    public static final int MAX_PAGE_SIZE = 100;
    /** 默认页码 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private PageUtil() {
    }

    /**
     * 归一化分页参数
     *
     * @param page     页码（<=0 时回退为 1）
     * @param pageSize 每页条数（<=0 时回退为 10；>100 时截断为 100）
     * @return int[0]=page, int[1]=pageSize
     */
    public static int[] normalize(int page, int pageSize) {
        int safePage = page < 1 ? DEFAULT_PAGE : page;
        int safeSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new int[]{safePage, safeSize};
    }

    /**
     * 实体分页 → VO 分页（保持 current/size/total 不变，仅替换 records）。
     * <p>消除各 admin 列表方法重复手写的 {@code new Page<>(current,size,total) + setRecords} 样板。
     *
     * @param page           实体分页（来自 mapper.selectPage）
     * @param batchConverter 整页 records → VO 列表（支持批量 enrich，如一次 IN 查询补齐关联名）
     * @param <E>            实体类型
     * @param <V>            VO 类型
     */
    public static <E, V> IPage<V> toVoPage(IPage<E> page, Function<List<E>, List<V>> batchConverter) {
        IPage<V> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(batchConverter.apply(page.getRecords()));
        return result;
    }
}
