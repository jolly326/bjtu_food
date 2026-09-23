package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.common.utils.SensitiveFilter;
import com.bjtufood.common.utils.UgcImageValidator;
import com.bjtufood.content.security.ContentSecurityService;
import com.bjtufood.review.dto.MyReviewVO;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.entity.User;
import com.bjtufood.common.utils.AuthStateUtil;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.entity.Dish;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    private final SensitiveFilter sensitiveFilter;
    private final ContentSecurityService contentSecurityService;

    /**
     * 菜品评价公开列表：时间倒序唯一口径（created_at DESC），可选「只看有图」筛选。
     */
    @Override
    public IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize, Integer hasImage) {
        int[] p = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByDishId(new Page<>(page, pageSize), dishId, hasImage);
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public IPage<MyReviewVO> listByUserId(Long userId, int page, int pageSize, Long dishId) {
        int[] p = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        // 我的评价：本人视角，公开列表的 is_hidden 过滤不适用——被管理员隐藏（is_hidden=1）的评价
        // 作者本人仍可见（MyReviewVO 的 isHidden 供端上标注「已被隐藏」）。排序固定时间倒序。
        // dishId 可选过滤：详情页判定「我是否已评价」并取回评价 ID（避免分页边界丢失）。
        // 2026-09-23 R9：返回类型由 ReviewVO 改 MyReviewVO（本人视角 11 字段），与公开链路分型。
        IPage<MyReviewVO> pageResult = reviewMapper.selectReviewPageByUserId(new Page<>(page, pageSize), userId, dishId);
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitReview(Long userId, Long dishId, ReviewReq req) {
        // 防御性拦截：评论内容为空或超长（@Valid 已做基础校验，此处兜底防止绕过）
        if (req.getContent() != null && req.getContent().length() > 500) {
            throw new BusinessException("评论内容不能超过500字");
        }
        if (reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getDishId, dishId)) > 0) {
            throw new BusinessException("您已评价过该菜品");
        }
        Review review = new Review();
        review.setUserId(userId);
        review.setDishId(dishId);
        review.setRating(req.getRating());
        String filteredContent = sensitiveFilter.filter(req.getContent());
        review.setContent(filteredContent);
        review.setIsHidden(0);

        // ---- UGC 准入门槛（project_spec §7.5 / §7.7：verified=1 且 openid 非空）----
        // 微信 msgSecCheck v2 必填 openid，故必须在机检之前前置双约束，否则口子敞开。
        User reviewUser = requireUgcAuthorizedUser(userId);

        // ---- 内容安全检测（产品定稿 2026-09-13：全部 UGC 过微信内容安全检测）----
        checkUgcText(reviewUser, filteredContent, 2);

        // 配图入库：COS 绝对地址列表 JSON（≤3 张，@Size(max=3) 前置校验，此处兜底）
        review.setImages(UgcImageValidator.encode(req.getImages(), "评价", imageUrlUtil));

        try {
            // uk_review_user_dish 唯一键兜底并发竞态：前置 selectCount 通过但插入瞬间已被他人抢先落库
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("您已评价过该菜品");
        }
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, dishId, req.getRating()));
        return review.getId();
    }

    /**
     * 重新评价（覆盖式）：覆盖同一行（评分/文字/配图），不新建行。
     * <p>
     * 覆盖语义（2026-09-20 拍板 D4）：created_at 刷新为当前（时间倒序下置顶）、is_hidden 重置 0、
     * 内容安全检测与首次发表同口径（违规 400 且原内容不变）、发既有 ReviewSubmittedEvent 重算聚合。
     * 鉴权 = 作者本人（非本人 403）；未认证由 Controller 的 @RequireVerified 给出 4031。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReview(Long id, Long userId, ReviewReq req) {
        if (req.getContent() != null && req.getContent().length() > 500) {
            throw new BusinessException("评论内容不能超过500字");
        }
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            // 错误码口径：200/400/401/403/4031/4001/500（4001 = 资源不存在，2026-09-23 新增，
            // 见 project_spec.md §7.40 R8）。本端点沿用既有 400 口径未改 —— 4001 首期仅在
            // GET /dishes/{id} 落地，其余端点随各自变更渐进对齐。
            throw new BusinessException(400, "评价不存在");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能修改自己的评价");
        }
        String filteredContent = sensitiveFilter.filter(req.getContent());
        // 与首次发表同口径：认证 + openid 准入 + 文本走微信内容安全检测 msgSecCheck（图片已在 /upload/images 链路过 imgSecCheck）
        User reviewUser = requireUgcAuthorizedUser(userId);
        checkUgcText(reviewUser, filteredContent, 2);
        String imagesJson = UgcImageValidator.encode(req.getImages(), "评价", imageUrlUtil);

        // 覆盖同一行：显式 set（含置 NULL / 重置 0 / 刷新 created_at），不新建行、唯一占位不变
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .set(Review::getRating, req.getRating())
                .set(Review::getContent, filteredContent)
                .set(Review::getImages, imagesJson)
                .set(Review::getIsHidden, 0)
                .set(Review::getCreatedAt, LocalDateTime.now()));
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), req.getRating()));
    }

    /**
     * UGC 作者准入：已认证（bind_email 非空，判据唯一真源 {@link AuthStateUtil}）且 openid 非空
     * （msgSecCheck v2 必填 openid）。
     *
     * @return 通过准入校验的用户实体
     */
    private User requireUgcAuthorizedUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !AuthStateUtil.isVerified(user.getBindEmail())) {
            // 4031 = 邮箱未认证（细分业务码，前端据此弹认证引导，区别于普通 403）
            throw new BusinessException(4031, "请先完成学号邮箱认证");
        }
        if (!StringUtils.hasText(user.getOpenid())) {
            // 已认证但无 openid（邮箱验证码登录账号）：msgSecCheck v2 无法调用，须先微信登录
            throw new BusinessException(403, "请使用微信登录后再发布评价");
        }
        return user;
    }

    /**
     * UGC 文本机检公共入口：取用户 openid 调 msgSecCheck v2（仅拦截，不落库安全态）。
     * <p>
     * 结果语义（2026-09-15 用户拍板取消人工复核）：risky 由 {@code checkText} 抛 400 拦截；
     * pass 与机检 review 均视为放行，不存在「待复核」落库值（sec_state 已全链退役）。
     * 边界（报告备案）：
     * 1. openid 为 NULL（历史学号账号）→ 跳过机审放行（msgSecCheck v2 openid 必填）；
     * 2. 微信凭据未配置（本地开发环境）→ 跳过机审放行；生产必须配置 WECHAT_APPID/WECHAT_SECRET。
     */
    private void checkUgcText(User user, String content, int scene) {
        if (!StringUtils.hasText(content)) {
            // 纯图评价/反馈：无文本可检，直接放行
            return;
        }
        String openid = user == null ? null : user.getOpenid();
        contentSecurityService.checkText(openid, content, scene);
    }

    /**
     * VO 配图绝对化（2026-09-23 R5）：{@code images} 已由 StringListTypeHandler 在持久层从
     * {@code review.images} 列直出为 {@code List<String>}，本方法只做「相对路径 → 绝对 URL」的业务转换。
     * COS 绝对地址原样返回（toAbsoluteUrl 对 http(s) 无损）；历史空值由 TypeHandler 归一为空列表。
     */
    private void fillImages(List<? extends ReviewVO> records) {
        if (records == null) {
            return;
        }
        for (ReviewVO vo : records) {
            List<String> images = vo.getImages();
            vo.setImages(images == null || images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id, Long userId) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            // 错误码口径：200/400/401/403/4031/4001/500（4001 = 资源不存在，2026-09-23 新增）；
            // 本端点沿用既有 400 口径未改（4001 首期仅在 GET /dishes/{id} 落地）
            throw new BusinessException(400, "评价不存在");
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(403, "只能删除自己的评价");
        }
        reviewMapper.deleteById(id);
        eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
    }

    @Override
    public IPage<ReviewAdminVO> listAllForAdmin(int page, int pageSize, Integer isHidden, Long userId, String keyword) {
        int[] norm = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = norm[0]; pageSize = norm[1];
        IPage<Review> pageResult = reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                // 2026-09-23 R6：Review::getUpdatedAt 已随 review.updated_at 列下线移除（该列不再存在）
                .select(Review::getId, Review::getUserId, Review::getDishId, Review::getRating,
                        Review::getContent, Review::getImages, Review::getIsHidden,
                        Review::getCreatedAt)
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
    public void deleteByAdmin(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review != null) {
            reviewMapper.deleteById(id);
            eventPublisher.publishEvent(new ReviewSubmittedEvent(this, review.getDishId(), review.getRating()));
        }
    }

    /**
     * 转换为管理端 VO（携带 is_hidden 处置字段与配图；sec_state 已随取消人工复核退役）
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
        vo.setCreatedAt(review.getCreatedAt());
        vo.setIsHidden(review.getIsHidden() != null ? review.getIsHidden() : 0);
        return vo;
    }
}
