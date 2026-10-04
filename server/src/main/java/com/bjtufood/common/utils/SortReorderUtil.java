package com.bjtufood.common.utils;

import com.bjtufood.common.dto.SortItem;
import com.bjtufood.common.exception.BusinessException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拖拽排序提交的**统一校验与归一**（六个 {@code PUT .../sort} 共用，口径见 docs/api/README.md）。
 *
 * <p>校验四件套（任一命中 → `400`「排序提交非法」）：
 * <ol>
 *   <li>空提交 / 含空行 / 缺 {@code id} 或 {@code order}；</li>
 *   <li>{@code id} 重复；</li>
 *   <li>{@code order} 重复；</li>
 *   <li><b>不是全量行</b>（提交的 id 集合 ≠ 该列表现有 id 集合，含未知 id 与缺行两种情形）。</li>
 * </ol>
 *
 * <p>通过后返回 {@code id → order} 映射，由各域自行落库（整体替换）。
 * 放在 {@code common} 是因为它是**跨域共用的提交协议**，不含任何业务域知识。
 */
public final class SortReorderUtil {

    private static final String MSG = "排序提交非法";

    /**
     * 校验并归一排序提交。
     *
     * @param items        提交行（全量）
     * @param existingIds  该列表**当前全部**行的 id（由各域查询后传入）
     * @return id → order（与 {@code items} 等长，已确认无重复）
     * @throws BusinessException 任一条校验不通过 → {@code 400}「排序提交非法」
     */
    public static Map<Long, Integer> resolve(List<SortItem> items, Collection<Long> existingIds) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(MSG);
        }
        Set<Long> submittedIds = new HashSet<>(items.size());
        Set<Integer> submittedOrders = new HashSet<>(items.size());
        Map<Long, Integer> result = new HashMap<>(items.size());
        for (SortItem item : items) {
            if (item == null || item.getId() == null || item.getOrder() == null) {
                throw new BusinessException(MSG);
            }
            if (!submittedIds.add(item.getId())) {
                throw new BusinessException(MSG);
            }
            if (!submittedOrders.add(item.getOrder())) {
                throw new BusinessException(MSG);
            }
            result.put(item.getId(), item.getOrder());
        }
        // 必须是「全量行」：既拦住未知 id，也拦住缺行（顺序不完整会让未提交行顺序随机）
        Set<Long> existing = new HashSet<>(existingIds == null ? List.of() : existingIds);
        if (!submittedIds.equals(existing)) {
            throw new BusinessException(MSG);
        }
        return result;
    }

    private SortReorderUtil() {
    }
}
