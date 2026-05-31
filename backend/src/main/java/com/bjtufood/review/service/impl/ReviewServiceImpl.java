package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize) {
        return reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                        .eq(Review::getDishId, dishId)
                        .eq(Review::getIsHidden, 0)
                        .orderByDesc(Review::getCreatedAt))
                .convert(this::toVO);
    }

    @Override
    public Long submitReview(Long userId, ReviewReq req) {
        if (reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getDishId, req.getDishId())) > 0) {
            throw new BusinessException("Review already exists");
        }
        Review review = new Review();
        review.setUserId(userId);
        review.setDishId(req.getDishId());
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setImages(req.getImages() == null ? null : String.join(",", req.getImages()));
        review.setIsHidden(0);
        reviewMapper.insert(review);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, req.getDishId(), req.getRating()));
        return review.getId();
    }

    @Override
    public void updateReview(Long id, Long userId, Integer rating, String content) {
        Review review = reviewMapper.selectById(id);
        if (review == null || !review.getUserId().equals(userId)) {
            throw new BusinessException("Review not found");
        }
        review.setRating(rating);
        review.setContent(content);
        reviewMapper.updateById(review);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), rating));
    }

    @Override
    public void deleteReview(Long id, Long userId) {
        Review review = reviewMapper.selectById(id);
        if (review == null || !review.getUserId().equals(userId)) {
            throw new BusinessException("Review not found");
        }
        reviewMapper.deleteById(id);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public IPage<ReviewVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Integer isDeleted) {
        return reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                        .eq(isHidden != null, Review::getIsHidden, isHidden)
                        .orderByDesc(Review::getCreatedAt))
                .convert(this::toVO);
    }

    @Override
    public void toggleHide(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("Review not found");
        }
        review.setIsHidden(review.getIsHidden() != null && review.getIsHidden() == 1 ? 0 : 1);
        reviewMapper.updateById(review);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public void deleteByAdmin(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review != null) {
            reviewMapper.deleteById(id);
            eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
        }
    }

    private ReviewVO toVO(Review review) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setUserId(review.getUserId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setImages(review.getImages() == null || review.getImages().isBlank()
                ? Collections.emptyList()
                : java.util.Arrays.asList(review.getImages().split(",")));
        vo.setCreatedAt(review.getCreatedAt());
        vo.setIsHidden(review.getIsHidden());
        vo.setHasSensitive(false);
        return vo;
    }
}
