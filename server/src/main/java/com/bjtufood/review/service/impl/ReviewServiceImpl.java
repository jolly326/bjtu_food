package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.dto.ReplyTotalVO;
import com.bjtufood.review.dto.UsefulResult;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.moment.service.MomentService;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.user.mapper.UserMapper;
import com.bjtufood.user.entity.User;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.entity.Dish;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    private final MomentService momentService;
    private final SensitiveFilter sensitiveFilter;

    @Override
    public IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize, String sort, Long userId, boolean withImage) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByDishId(new Page<>(page, pageSize), dishId, sort, withImage)
                .convert(this::enrichImages);
        // 楼中楼：将子回复（parent_id 非 NULL）归组到对应顶层评价的 replies，仅保留顶层评价分页
        assembleReplies(pageResult);
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
        assembleReplies(pageResult);
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
        assembleReplies(pageResult);
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
    public IPage<ReviewVO> listRepliesByParentId(Long parentId, int page, int pageSize, Long userId) {
        if (parentId == null) {
            throw new BusinessException("父评价ID不能为空");
        }
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> result = reviewMapper.selectRepliesPageByParentId(new Page<>(page, pageSize), parentId)
                .convert(this::enrichImages);
        // 每个直接子回复再带出各自的楼中楼孙回复窗口（复用 assembleReplies）
        assembleReplies(result);
        if (userId != null) {
            markUseful(result.getRecords(), userId);
        }
        return result;
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
            // 未标记 → 标记：插入记录 + 计数原子 +1（uk_useful_user_review 唯一键防并发重复）
            ReviewUseful useful = new ReviewUseful();
            useful.setUserId(userId);
            useful.setReviewId(reviewId);
            try {
                reviewUsefulMapper.insert(useful);
            } catch (DuplicateKeyException e) {
                // 并发下同一用户重复提交：唯一键已拦截，视为「已标记」幂等返回，不再重复加计数
                throw new BusinessException("你已经标记过这条评价");
            }
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

    /**
     * 楼中楼归组：将平铺结果中的子回复（parent_id 非 NULL）拼回对应父评价的 replies，
     * 并移除分页记录中的子回复行，仅保留顶层评价（replies 内含子回复）。
     * <p>
     * 子回复按 created_at 升序，保证楼中楼阅读顺序正确。
     */
    /** 楼中楼嵌套最大深度（顶层=0，往下递归 3 层封顶，防数据失控） */
    private static final int REPLY_MAX_DEPTH = 3;
    /** 每个父节点窗口返回的子回复条数（首屏只加载最近 N 条，更多由 repliesHasMore 提示） */
    private static final int REPLY_WINDOW_LIMIT = 5;

    /**
     * 楼中楼归组：列表查询已仅返回顶层评价（parent_id IS NULL），
     * 此处以顶层评价为种子做多轮 BFS，逐层批量查子回复并构建完整嵌套树（封顶 3 层）。
     * 每层一次 IN 批量查询，避免 N+1；窗口函数限制每父最近 REPLY_WINDOW_LIMIT 条，
     * 并结合总数统计写入 repliesHasMore（供前端「查看全部」占位）。
     */
    private void assembleReplies(IPage<ReviewVO> pageResult) {
        List<ReviewVO> topLevel = pageResult.getRecords();
        if (topLevel.isEmpty()) {
            return;
        }
        // id → VO 索引，用于挂载子回复（含顶层与各层子回复节点）
        Map<Long, ReviewVO> index = new HashMap<>();
        for (ReviewVO top : topLevel) {
            index.put(top.getId(), top);
        }
        // 当前层待查父节点 id
        List<Long> currentLevelIds = topLevel.stream().map(ReviewVO::getId).toList();
        for (int depth = 0; depth < REPLY_MAX_DEPTH && !currentLevelIds.isEmpty(); depth++) {
            // 批量查当前层各父节点的最近子回复
            List<ReviewVO> children = reviewMapper.selectRepliesByParentIds(currentLevelIds, REPLY_WINDOW_LIMIT);
            if (children.isEmpty()) {
                break;
            }
            // 按 parentId 归组（SQL 已按 created_at ASC 排序，直接追加即顺序正确）
            Map<Long, List<ReviewVO>> childrenMap = new HashMap<>();
            for (ReviewVO child : children) {
                childrenMap.computeIfAbsent(child.getParentId(), k -> new ArrayList<>()).add(child);
            }
            // 批量统计各父真实子回复总数，用于判定 repliesHasMore
            Map<Long, Long> totalMap = new HashMap<>();
            for (ReplyTotalVO t : reviewMapper.selectReplyTotalByParentIds(currentLevelIds)) {
                totalMap.put(t.getParentId(), t.getTotal());
            }
            // 挂载到父节点并收集下一层父 id
            List<Long> nextLevelIds = new ArrayList<>();
            for (Map.Entry<Long, List<ReviewVO>> e : childrenMap.entrySet()) {
                ReviewVO parent = index.get(e.getKey());
                if (parent == null) {
                    continue;
                }
                List<ReviewVO> subs = e.getValue();
                parent.setReplies(subs);
                Long total = totalMap.get(e.getKey());
                parent.setRepliesHasMore(total != null && total > subs.size());
                for (ReviewVO child : subs) {
                    index.put(child.getId(), child);
                    nextLevelIds.add(child.getId());
                }
            }
            currentLevelIds = nextLevelIds;
        }
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
        String filteredContent = sensitiveFilter.filter(req.getContent());
        review.setContent(filteredContent);
        review.setImages(JsonListUtil.toJson(req.getImages()));
        review.setIsHidden(0);
        reviewMapper.insert(review);
        // 评价与动态打通：勾选"同步到动态"且评价有正文时，生成 approved 动态直接上广场（评价可见即动态可见）
        boolean shareToMoment = Boolean.TRUE.equals(req.getShareToMoment());
        if (shareToMoment && StringUtils.hasText(filteredContent)) {
            List<String> images = req.getImages() == null ? List.of() : req.getImages();
            momentService.publishFromReview(userId, filteredContent, images, req.getDishId());
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
        review.setContent(sensitiveFilter.filter(content));
        reviewMapper.updateById(review);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), rating));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long replyReview(Long userId, Long parentId, String content) {
        if (parentId == null) {
            throw new BusinessException("回复目标评价ID不能为空");
        }
        if (content == null || !StringUtils.hasText(content.trim())) {
            throw new BusinessException("回复内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException("回复内容不能超过500字");
        }
        Review parent = reviewMapper.selectById(parentId);
        if (parent == null || parent.getIsHidden() != null && parent.getIsHidden() == 1) {
            throw new BusinessException("被回复的评价不存在或已被隐藏");
        }
        // 取父评价者昵称作为被回复者（冗余存储，前端直接展示「@昵称」）
        String parentNickname = parent.getUserId() != null
                ? reviewMapper.selectNicknameByUserId(parent.getUserId())
                : null;
        Review reply = new Review();
        reply.setUserId(userId);
        reply.setDishId(parent.getDishId());
        reply.setRating(0); // 回复不计分
        reply.setContent(sensitiveFilter.filter(content.trim()));
        reply.setImages(null);
        reply.setIsHidden(0);
        reply.setParentId(parentId);
        reply.setReplyToNickname(parentNickname);
        reviewMapper.insert(reply);
        return reply.getId();
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
        // BFS 收集该评价的全部后代（含多层楼中楼子回复），避免删顶层后子回复成孤儿数据
        List<Long> allIds = new ArrayList<>();
        allIds.add(id);
        List<Long> currentIds = new ArrayList<>();
        currentIds.add(id);
        for (int depth = 0; depth < REPLY_MAX_DEPTH && !currentIds.isEmpty(); depth++) {
            List<Long> children = reviewMapper.selectReplyIdsByParentIds(currentIds);
            allIds.addAll(children);
            currentIds = children;
        }
        // 物理删除：自身 + 全部后代（review 表 parent_id 无外键级联，须显式清理）
        reviewMapper.deleteBatchIds(allIds);
        // 批量清理这些评价的「有用」关联 + 重算菜品评分
        reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>()
                .in(ReviewUseful::getReviewId, allIds));
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Integer isDeleted, Long userId, String keyword) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        // 显式指定查询列，排除 useful_count（该列由末尾 ALTER / review_useful 表聚合维护，
        // 在仅建了原始 review 表的旧库上不存在，selectPage 全列查询会命中 Unknown column → 500）。
        // 管理端评价列表当前不展示 usefulCount（见 ReviewReviewView.vue 列定义），排除无功能损失。
        IPage<Review> pageResult = reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                        .select(Review::getId, Review::getUserId, Review::getDishId, Review::getRating,
                                Review::getContent, Review::getImages, Review::getIsHidden,
                                Review::getCreatedAt, Review::getUpdatedAt)
                        .eq(isHidden != null, Review::getIsHidden, isHidden)
                        .eq(userId != null, Review::getUserId, userId)
                        // 关键词模糊匹配评价正文，仅当显式传入时生效
                        .like(StringUtils.hasText(keyword), Review::getContent, keyword == null ? null : keyword.trim())
                        .orderByDesc(Review::getCreatedAt));
        List<ReviewAdminVO> vos = enrichAdminBatch(pageResult.getRecords());
        IPage<ReviewAdminVO> result = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        result.setRecords(vos);
        return result;
    }

    /**
     * 管理端评价列表批量 enrich：补齐评价者昵称/头像、菜品名，避免前端本地 find 退化。
     */
    private List<ReviewAdminVO> enrichAdminBatch(List<Review> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = records.stream().map(Review::getUserId).filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> dishIds = records.stream().map(Review::getDishId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? new HashMap<>()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds)).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, Dish> dishMap = dishIds.isEmpty() ? new HashMap<>()
                : dishMapper.selectList(new LambdaQueryWrapper<Dish>().in(Dish::getId, dishIds)).stream()
                .collect(Collectors.toMap(Dish::getId, d -> d, (a, b) -> a));
        List<ReviewAdminVO> vos = new ArrayList<>(records.size());
        for (Review r : records) {
            ReviewAdminVO vo = toAdminVO(r);
            User u = r.getUserId() == null ? null : userMap.get(r.getUserId());
            vo.setUserNickname(u != null ? u.getNickname() : null);
            vo.setUserAvatar(u != null ? imageUrlUtil.toAbsoluteUrl(u.getAvatar()) : null);
            Dish d = r.getDishId() == null ? null : dishMap.get(r.getDishId());
            vo.setDishName(d != null ? d.getName() : null);
            vos.add(vo);
        }
        return vos;
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
        try {
            reviewUsefulMapper.insert(useful);
        } catch (DuplicateKeyException e) {
            // 并发下先查后插存在竞态，唯一键兜底：统一转为业务提示，避免 500
            throw new BusinessException("你已经喜欢过这条评价");
        }
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
