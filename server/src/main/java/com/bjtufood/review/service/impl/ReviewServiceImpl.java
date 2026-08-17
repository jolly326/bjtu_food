package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.dto.UsefulResult;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.moment.service.MomentService;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import com.bjtufood.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    private final MomentService momentService;

    @Override
    public IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize, String sort, Long userId, boolean withImage) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByDishId(new Page<>(page, pageSize), dishId, sort, withImage)
                .convert(this::enrichImages);
        if (userId != null) {
            markUseful(pageResult.getRecords(), userId);
        }
        return pageResult;
    }

    @Override
    public IPage<ReviewVO> listByStallId(Long stallId, int page, int pageSize, String sort, Long userId, boolean withImage) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByStallId(new Page<>(page, pageSize), stallId, sort, withImage)
                .convert(this::enrichImages);
        if (userId != null) {
            markUseful(pageResult.getRecords(), userId);
        }
        return pageResult;
    }

    @Override
    public IPage<ReviewVO> listByCanteenId(Long canteenId, int page, int pageSize, String sort, Long userId, boolean withImage) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByCanteenId(new Page<>(page, pageSize), canteenId, sort, withImage)
                .convert(this::enrichImages);
        if (userId != null) {
            markUseful(pageResult.getRecords(), userId);
        }
        return pageResult;
    }

    @Override
    public BigDecimal getAvgRatingByStallId(Long stallId) {
        BigDecimal avg = reviewMapper.selectAvgRatingByStallId(stallId);
        return avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    @Override
    public IPage<ReviewVO> listByUserId(Long userId, int page, int pageSize) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        return reviewMapper.selectReviewPageByUserId(new Page<>(page, pageSize), userId, null)
                .convert(this::enrichImages);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsefulResult toggleUseful(Long userId, Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        ReviewUseful exist = reviewUsefulMapper.selectOne(new LambdaQueryWrapper<ReviewUseful>()
                .eq(ReviewUseful::getUserId, userId)
                .eq(ReviewUseful::getReviewId, reviewId));
        UsefulResult result = new UsefulResult();
        if (exist != null) {
            // 已标记 → 取消：删除记录 + 计数原子 -1
            reviewUsefulMapper.deleteById(exist.getId());
            reviewMapper.changeUsefulCount(reviewId, -1);
            result.setUseful(false);
        } else {
            // 未标记 → 标记：插入记录 + 计数原子 +1（uk_user_review 唯一键防并发重复）
            ReviewUseful useful = new ReviewUseful();
            useful.setUserId(userId);
            useful.setReviewId(reviewId);
            reviewUsefulMapper.insert(useful);
            reviewMapper.changeUsefulCount(reviewId, 1);
            result.setUseful(true);
        }
        // 原子增减后回读最新计数（避免返回过期的读-改-写值）
        Review latest = reviewMapper.selectById(reviewId);
        result.setUsefulCount(latest == null ? 0 : (latest.getUsefulCount() == null ? 0 : latest.getUsefulCount()));
        return result;
    }

    /**
     * 回写当前用户对评价列表的「有用」标记状态（避免泄露给非登录用户）
     */
    private void markUseful(List<ReviewVO> records, Long userId) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(ReviewVO::getId).toList();
        Set<Long> markedIds = reviewUsefulMapper.selectList(new LambdaQueryWrapper<ReviewUseful>()
                        .eq(ReviewUseful::getUserId, userId)
                        .in(ReviewUseful::getReviewId, ids))
                .stream()
                .map(ReviewUseful::getReviewId)
                .collect(Collectors.toSet());
        records.forEach(r -> r.setUseful(markedIds.contains(r.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitReview(Long userId, ReviewReq req) {
        // 防御性拦截：评论内容为空或超长（@Valid 已做基础校验，此处兜底防止绕过）
        if (req.getContent() != null && req.getContent().length() > 500) {
            throw new BusinessException("评论内容不能超过500字");
        }
        if (reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getDishId, req.getDishId())) > 0) {
            throw new BusinessException("Already reviewed this dish");
        }
        Review review = new Review();
        review.setUserId(userId);
        review.setDishId(req.getDishId());
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setImages(JsonListUtil.toJson(req.getImages()));
        review.setIsHidden(0);
        reviewMapper.insert(review);
        // 评价与动态打通：勾选"同步到动态"且评价有正文时，生成 approved 动态直接上广场（评价可见即动态可见）
        boolean shareToMoment = Boolean.TRUE.equals(req.getShareToMoment());
        if (shareToMoment && StringUtils.hasText(req.getContent())) {
            List<String> images = req.getImages() == null ? List.of() : req.getImages();
            momentService.publishFromReview(userId, req.getContent(), images, req.getDishId());
        }
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, req.getDishId(), req.getRating()));
        return review.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id, Long userId) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(404, "评价不存在");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己的评价");
        }
        // 物理删除：级联清理该评价的「有用」关联 + 重算菜品评分
        reviewMapper.deleteById(id);
        reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>()
                .eq(ReviewUseful::getReviewId, id));
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Integer isDeleted, Long userId, String keyword) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        // 显式指定查询列，排除 useful_count（该列由末尾 ALTER / review_useful 表聚合维护，
        // 在仅建了原始 review 表的旧库上不存在，selectPage 全列查询会命中 Unknown column → 500）。
        // 管理端评价列表当前不展示 usefulCount（见 ReviewReviewView.vue 列定义），排除无功能损失。
        return reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                        .select(Review::getId, Review::getUserId, Review::getDishId, Review::getRating,
                                Review::getContent, Review::getImages, Review::getIsHidden,
                                Review::getCreatedAt, Review::getUpdatedAt)
                        .eq(isHidden != null, Review::getIsHidden, isHidden)
                        .eq(userId != null, Review::getUserId, userId)
                        // 关键词模糊匹配评价正文，仅当显式传入时生效
                        .like(StringUtils.hasText(keyword), Review::getContent, keyword == null ? null : keyword.trim())
                        .orderByDesc(Review::getCreatedAt))
                .convert(this::toAdminVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setHidden(Long id, boolean hidden) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("Review not found");
        }
        review.setIsHidden(hidden ? 1 : 0);
        reviewMapper.updateById(review);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public void likeReview(Long userId, Long reviewId) {
        ReviewUseful exist = reviewUsefulMapper.selectOne(
                new LambdaQueryWrapper<ReviewUseful>()
                        .eq(ReviewUseful::getUserId, userId)
                        .eq(ReviewUseful::getReviewId, reviewId));
        if (exist != null) {
            throw new BusinessException("你已经喜欢过这条评价");
        }
        ReviewUseful useful = new ReviewUseful();
        useful.setUserId(userId);
        useful.setReviewId(reviewId);
        reviewUsefulMapper.insert(useful);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAdmin(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review != null) {
            reviewMapper.deleteById(id);
            eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
        }
    }

    /**
     * 转换为管理端 VO（携带 is_hidden/has_sensitive 审核字段）
     */
    private ReviewAdminVO toAdminVO(Review review) {
        ReviewAdminVO vo = new ReviewAdminVO();
        vo.setId(review.getId());
        vo.setUserId(review.getUserId());
        vo.setDishId(review.getDishId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(review.getImages()));
        vo.setCreatedAt(review.getCreatedAt());
        vo.setIsHidden(review.getIsHidden() != null ? review.getIsHidden() : 0);
        vo.setHasSensitive(false);
        return vo;
    }

    /**
     * 对公开 ReviewVO 的 imagesJson 字段进行 URL 解析（mapper 联表已填充 userNickname/userAvatar/imagesJson）
     */
    private ReviewVO enrichImages(ReviewVO vo) {
        if (vo == null) {
            return null;
        }
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(vo.getImagesJson()));
        return vo;
    }
}
