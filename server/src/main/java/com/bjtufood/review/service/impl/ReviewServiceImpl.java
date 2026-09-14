package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.constant.SecStateConst;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.content.security.SecSuggest;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.dto.UsefulResult;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.entity.User;
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

    /** 内容安全状态常量：机检待人工复核 / 人工复核不通过（均对他端不可见，作者本人可见）。真源：{@link SecStateConst} */
    public static final String SEC_STATE_PASS = SecStateConst.PASS;
    public static final String SEC_STATE_REVIEW = SecStateConst.REVIEW;
    public static final String SEC_STATE_REJECTED = SecStateConst.REJECTED;

    /** UGC 配图上限（张） */
    private static final int MAX_IMAGES = 3;

    private final ReviewMapper reviewMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    private final SensitiveFilter sensitiveFilter;
    private final ContentSecurityService contentSecurityService;

    /**
     * 菜品评价公开列表。
     * <p>
     * 排序（2026-09-14 §7.14 B）：sort 缺省/sort=useful 按「有用数」置顶，sort=latest 按时间倒序。
     */
    @Override
    public IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize, String sort, Long userId) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByDishId(new Page<>(page, pageSize), dishId, sort, userId);
        // 评价扁平化：列表接口直接返回扁平顶层评价（无楼中楼），见 project_spec 决策
        if (userId != null) {
            markUseful(pageResult.getRecords(), userId);
        }
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public IPage<ReviewVO> listByStallId(Long stallId, int page, int pageSize, String sort, Long userId) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByStallId(new Page<>(page, pageSize), stallId, sort, userId);
        // 评价扁平化：列表接口直接返回扁平顶层评价（无楼中楼）
        if (userId != null) {
            markUseful(pageResult.getRecords(), userId);
        }
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public IPage<ReviewVO> listByCanteenId(Long canteenId, int page, int pageSize, String sort, Long userId) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByCanteenId(new Page<>(page, pageSize), canteenId, sort, userId);
        // 评价扁平化：列表接口直接返回扁平顶层评价（无楼中楼）
        if (userId != null) {
            markUseful(pageResult.getRecords(), userId);
        }
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public IPage<ReviewVO> listByUserId(Long userId, int page, int pageSize) {
        int[] p = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        // 我的评价（2026-09-14 §7.14 C）：本人视角，公开列表的 is_hidden/sec_state 过滤均不适用——
        // 被管理员隐藏（is_hidden=1）的评价作者本人仍可见（VO 的 isHidden 供端上标注「已被隐藏」），
        // 机检待复核（sec_state=review）同样放行（端上提示「审核中」）。
        // 排序固定按发表时间倒序（sort=latest 显式传入，本人评价按时间更自然；与公开列表默认「有用数置顶」解耦）
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByUserId(new Page<>(page, pageSize), userId, "latest");
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public BigDecimal getAvgRatingByStallId(Long stallId) {
        BigDecimal avg = reviewMapper.selectAvgRatingByStallId(stallId);
        return avg != null ? avg.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UsefulResult toggleUseful(Long userId, Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        // L2 修复：对已被管理端隐藏（is_hidden=1）的评价不可再点「有用」，保持对外可见口径一致
        if (Integer.valueOf(1).equals(review.getIsHidden())) {
            throw new BusinessException("该评价不可标记");
        }
        ReviewUseful exist = reviewUsefulMapper.selectOne(new LambdaQueryWrapper<ReviewUseful>()
                .eq(ReviewUseful::getUserId, userId)
                .eq(ReviewUseful::getReviewId, reviewId));
        UsefulResult result = new UsefulResult();
        if (exist != null) {
            // 已标记 → 取消：删除记录 + 计数原子 -1。
            // 并发取消守卫：仅当真正删除到 1 行才减计数，避免两请求同时读到 exist、
            // 都 deleteById（第二个 0 行 no-op）却各减一次计数导致 useful_count 漂移。
            int deleted = reviewUsefulMapper.deleteById(exist.getId());
            if (deleted > 0) {
                reviewMapper.changeUsefulCount(reviewId, -1);
            }
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
        review.setIsHidden(0);

        // ---- UGC 准入门槛（project_spec §7.5 / §7.7：verified=1 且 openid 非空）----
        // 仅约束「公开可见的评价内容」：微信 msgSecCheck v2 必填 openid，而 checkUgcText 在
        // openid 为 NULL 时会走「跳过机审放行」分支，故必须在机检之前前置双约束，否则口子敞开。
        // 反馈 / 投稿 / 举报按 §7.7 免认证，不受此约束。
        User reviewUser = userMapper.selectById(userId);
        if (reviewUser == null
                || reviewUser.getVerified() == null
                || reviewUser.getVerified() != 1) {
            // 4031 = 邮箱未认证（细分业务码，前端据此弹认证引导，区别于普通 403）
            throw new BusinessException(4031, "请先完成学号邮箱认证");
        }
        if (!StringUtils.hasText(reviewUser.getOpenid())) {
            // 已认证但无 openid（邮箱验证码登录账号）：msgSecCheck v2 无法调用，须先微信登录
            throw new BusinessException(403, "请使用微信登录后再发布评价");
        }

        // ---- 内容安全检测（产品定稿 2026-09-13：全部 UGC 过微信内容安全检测）----
        // 文本 msgSecCheck v2（scene=2 评论）：risky 由 checkText 统一拦截（400），
        // review 态落库 sec_state='review'（对他端不可见，作者本人可见并提示「审核中」）。
        // 注：openid 为 NULL 的情形已由上方准入校验拦截，此处不会走到「跳过机审放行」分支。
        review.setSecState(checkUgcText(userId, filteredContent, 2));

        // 配图入库：COS 绝对地址列表 JSON（≤3 张，@Size(max=3) 前置校验，此处兜底）
        review.setImages(encodeImages(req.getImages()));

        try {
            // uk_review_user_dish 唯一键兜底并发竞态：前置 selectCount 通过但插入瞬间已被他人抢先落库
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("您已评价过该菜品");
        }
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, req.getDishId(), req.getRating()));
        return review.getId();
    }

    /**
     * UGC 文本机检公共入口：取当前用户 openid 调 msgSecCheck v2。
     * <p>
     * 返回落库的 sec_state：pass / review（risky 已由 checkText 抛 400，不会返回）。
     * 边界（报告备案）：
     * 1. openid 为 NULL（历史学号账号）→ 跳过机审放行（msgSecCheck v2 openid 必填）；
     * 2. 微信凭据未配置（本地开发环境）→ 跳过机审放行；生产必须配置 WECHAT_APPID/WECHAT_SECRET。
     */
    private String checkUgcText(Long userId, String content, int scene) {
        if (!StringUtils.hasText(content)) {
            // 纯图评价/反馈：无文本可检，直接通过
            return SEC_STATE_PASS;
        }
        User user = userMapper.selectById(userId);
        String openid = user == null ? null : user.getOpenid();
        SecSuggest suggest = contentSecurityService.checkText(openid, content, scene);
        return suggest == SecSuggest.REVIEW ? SEC_STATE_REVIEW : SEC_STATE_PASS;
    }

    /**
     * 校验并序列化 UGC 配图：≤3 张、每项必须为受信任的 COS 绝对地址。
     * 仅允许 {@code POST /upload/images} 链路产出的 COS URL，防止 UGC 配图沦为任意 URL 载体。
     */
    private String encodeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException("评价配图最多 " + MAX_IMAGES + " 张");
        }
        List<String> normalized = images.stream().map(String::trim).filter(StringUtils::hasText).toList();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MAX_IMAGES) {
            throw new BusinessException("评价配图最多 " + MAX_IMAGES + " 张");
        }
        for (String url : normalized) {
            if (!imageUrlUtil.isValidCosUgcUrl(url)) {
                throw new BusinessException("图片地址不合法，请重新上传");
            }
        }
        return JsonListUtil.toJson(normalized);
    }

    /**
     * VO 配图填充：images_json（mapper 直填的 JSON 原文）解析为 images 数组。
     * COS 绝对地址原样返回（toAbsoluteUrl 对 http(s) 无损），历史空值归一为空列表。
     */
    private void fillImages(List<ReviewVO> records) {
        if (records == null) {
            return;
        }
        for (ReviewVO vo : records) {
            List<String> images = JsonListUtil.parseStringList(vo.getImagesJson());
            vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
            vo.setImagesJson(null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id, Long userId) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            // 错误码口径：仅 200/400/401/403/4031/500，资源不存在按 400 业务校验返回
            throw new BusinessException(400, "评价不存在");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己的评价");
        }
        // 评价扁平化：评价无楼中楼后代，物理删除自身并清理「有用」关联即可（无需 BFS 后代收集）
        reviewMapper.deleteById(id);
        reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>()
                .eq(ReviewUseful::getReviewId, id));
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Integer isHidden, String secState, Long userId, String keyword) {
        int[] norm = com.bjtufood.common.util.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        // 显式指定查询列，排除 useful_count（该列由末尾 ALTER / review_useful 表聚合维护，
        // 在仅建了原始 review 表的旧库上不存在，selectPage 全列查询会命中 Unknown column → 500）。
        // 管理端评价列表当前不展示 usefulCount（见 ReviewReviewView.vue 列定义），排除无功能损失。
        IPage<Review> pageResult = reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                        .select(Review::getId, Review::getUserId, Review::getDishId, Review::getRating,
                                Review::getContent, Review::getImages, Review::getSecState, Review::getIsHidden,
                                Review::getCreatedAt, Review::getUpdatedAt)
                        .eq(isHidden != null, Review::getIsHidden, isHidden)
                        // 内容安全状态筛选（管理端复核队列：secState=review 捞待人工复核）
                        .eq(StringUtils.hasText(secState), Review::getSecState,
                                secState == null ? null : secState.trim().toLowerCase())
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
                : userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getNickname, User::getAvatar)
                        .in(User::getId, userIds)).stream()
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
    @Transactional(rollbackFor = Exception.class)
    public void setSecState(Long id, String state) {
        // 枚举校验：本接口契约仅允许 pass/rejected（review 由机检写入，管理端只能改人工结论）
        String normalized = state == null ? "" : state.trim().toLowerCase();
        if (!SEC_STATE_PASS.equals(normalized) && !SEC_STATE_REJECTED.equals(normalized)) {
            throw new BusinessException("state 仅允许 pass 或 rejected");
        }
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        review.setSecState(normalized);
        reviewMapper.updateById(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAdmin(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review != null) {
            reviewMapper.deleteById(id);
            // 清理「有用」关联孤儿行，避免 review_useful 堆积并与 useful_count 长期不一致
            reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>()
                    .eq(ReviewUseful::getReviewId, id));
            eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
        }
    }

    /**
     * 转换为管理端 VO（携带 is_hidden/sec_state 审核字段与配图）
     */
    private ReviewAdminVO toAdminVO(Review review) {
        ReviewAdminVO vo = new ReviewAdminVO();
        vo.setId(review.getId());
        vo.setUserId(review.getUserId());
        vo.setDishId(review.getDishId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        List<String> images = JsonListUtil.parseStringList(review.getImages());
        vo.setImages(images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        vo.setSecState(StringUtils.hasText(review.getSecState()) ? review.getSecState() : SEC_STATE_PASS);
        vo.setCreatedAt(review.getCreatedAt());
        vo.setIsHidden(review.getIsHidden() != null ? review.getIsHidden() : 0);
        return vo;
    }
}
