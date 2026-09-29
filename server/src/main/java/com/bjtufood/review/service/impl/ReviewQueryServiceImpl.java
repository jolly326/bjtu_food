package com.bjtufood.review.service.impl;

import com.bjtufood.review.dto.StallAvgRatingVO;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.service.ReviewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * {@link ReviewQueryService} 实现——review 域内唯一持有 ReviewMapper 的只读服务。
 * <p>
 * 刻意零业务依赖（不注入 DishService/UserService 等），使 canteen 等模块可安全注入而不引入依赖环
 * （设计动机见 {@link ReviewQueryService} 类注释）。
 */
@Service
@RequiredArgsConstructor
public class ReviewQueryServiceImpl implements ReviewQueryService {

    private final ReviewMapper reviewMapper;

    @Override
    public List<StallAvgRatingVO> findAvgRatingByStallIds(Collection<Long> stallIds) {
        if (stallIds == null || stallIds.isEmpty()) {
            return List.of();
        }
        return reviewMapper.selectAvgRatingByStallIds(stallIds);
    }
}
