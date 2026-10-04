package com.bjtufood.common.utils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bjtufood.common.exception.BusinessException;

/**
 * 唯一性校验工具：把各 Service 里「selectCount(wrapper) &gt; 0 → 抛『已存在』」的惯用法收敛到一处，
 * 消除跨模块复制粘贴（canteen / stall / dish-attribute / dish-category / dish-view / report-reason / feedback）。
 */
public final class DuplicateGuard {

    private DuplicateGuard() {
    }

    /** 命中即抛 {@code BusinessException(400, message)}。 */
    public static <T> void assertUnique(BaseMapper<T> mapper, Wrapper<T> wrapper, String message) {
        assertUnique(mapper, wrapper, 400, message);
    }

    /** 命中即抛 {@code BusinessException(code, message)}。 */
    public static <T> void assertUnique(BaseMapper<T> mapper, Wrapper<T> wrapper, int code, String message) {
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(code, message);
        }
    }
}
