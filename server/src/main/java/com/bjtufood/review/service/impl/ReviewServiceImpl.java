package com.bjtufood.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.moderation.service.LocalSensitiveFilter;
import com.bjtufood.common.utils.UgcImageValidator;
import com.bjtufood.moderation.service.ContentSecurityService;
import com.bjtufood.review.dto.MyReviewVO;
import com.bjtufood.review.dto.ReviewReq;
import com.bjtufood.review.dto.ReviewVO;
import com.bjtufood.review.dto.ReviewAdminVO;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.event.ReviewSubmittedEvent;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.service.ReviewService;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.dto.UserBriefVO;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.dish.service.DishService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    /**
     * 落库事务边界（2026-09-29 性能修正，与 {@link com.bjtufood.feedback.service.impl.FeedbackPersister} 同源）：
     * {@code submitReview} / {@code updateReview} 的 {@code @Transactional} 原先从方法入口就开始、横跨微信机审的
     * HTTP 外呼 ⇒ 期间一直占用数据库连接；HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
     * 现改为「先机审（无事务）→ 再落库（开事务）」。
     * <p>
     * 本类同时承载「写库 + 发 {@code ReviewSubmittedEvent}」：监听器为 AFTER_COMMIT 相位，
     * 事件必须发布在事务内，否则评分永不重算（详见 {@link ReviewPersister} 类注释）。
     */
    private final ReviewPersister reviewPersister;
    /** 跨域只读契约：UGC 准入上下文 + 管理端列表作者投影（P0-1，替代 UserMapper 直连） */
    private final UserService userService;
    /** 跨域只读契约：管理端列表菜品名（P0-1，替代 DishMapper 直连） */
    private final DishService dishService;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlUtil imageUrlUtil;
    private final LocalSensitiveFilter localSensitiveFilter;
    private final ContentSecurityService contentSecurityService;

    /**
     * 评价是否存在且公开可见（{@code is_hidden=0}）——供 feedback 域举报目标校验消费。
     * 已隐藏 / 已删除的评价对外等价于不存在，不可被举报。
     */
    @Override
    public boolean existsVisibleById(Long id) {
        return id != null && reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getIsHidden, 0)) > 0;
    }

    /**
     * 菜品评价公开列表：时间倒序唯一口径（created_at DESC）。
     */
    @Override
    public IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize) {
        int[] p = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        IPage<ReviewVO> pageResult = reviewMapper.selectReviewPageByDishId(new Page<>(page, pageSize), dishId);
        fillImages(pageResult.getRecords());
        return pageResult;
    }

    @Override
    public IPage<MyReviewVO> listByUserId(Long userId, int page, int pageSize, Long dishId) {
        int[] p = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        // 我的评价：对评价全集按用户过滤拆分（is_hidden=0，与公开列表同口径——被隐藏的评价
        // 不对客户端含作者本人返回）。排序固定时间倒序。
        // dishId 可选过滤：详情页判定「我是否已评价」并取回评价 ID（避免分页边界丢失）。
        // R9：返回类型由 ReviewVO 改 MyReviewVO（本人视角 10 字段），与公开链路分型。
        IPage<MyReviewVO> pageResult = reviewMapper.selectReviewPageByUserId(new Page<>(page, pageSize), userId, dishId);
        fillMyReviewImages(pageResult.getRecords());
        return pageResult;
    }

    /**
     * 发表评价（**重复提交即覆盖**，2026-09-30 简化）。
     * <p>
     * 同一用户对同一菜品只有一条评价：不存在则 INSERT，已存在则**覆盖同一行**
     * （评分 / 文字 / 配图 / created_at 刷新 / is_hidden 重置 0）—— 端上不再区分首评与重评，
     * 也无需先判定「我是否已评价」。
     * <p>
     * <b>本方法刻意不加 {@code @Transactional}</b>：事务边界收窄到落库一步
     * （{@link ReviewPersister#insertAndPublish} / {@link ReviewPersister#updateAndPublish}），
     * 使微信机审的外呼期间不占用数据库连接。
     */
    @Override
    public Long submitReview(Long userId, Long dishId, ReviewReq req) {
        // 防御性拦截：评论内容为空或超长（@Valid 已做基础校验，此处兜底防止绕过）
        if (req.getContent() != null && req.getContent().length() > 500) {
            throw new BusinessException("评论内容不能超过500字");
        }
        // 唯一键 uk_review_user_dish：至多一条，取已有行决定 INSERT / 覆盖
        Review existing = reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getDishId, dishId)
                .last("LIMIT 1"));
        String filteredContent = localSensitiveFilter.filter(req.getContent());

        // ---- UGC 准入门槛（project_spec §7.5 / §7.7：verified=1 且 openid 非空）----
        // 微信 msgSecCheck v2 必填 openid，故必须在机检之前前置双约束，否则口子敞开。
        UserAuthContextVO reviewUser = requireUgcAuthorizedUser(userId);

        // ---- 内容安全检测（产品定稿 2026-09-13：全部 UGC 过微信内容安全检测）----
        checkUgcText(reviewUser, filteredContent, 2);

        // 配图入库：COS 绝对地址列表 JSON（≤3 张，@Size(max=3) 前置校验，此处兜底）
        String imagesJson = UgcImageValidator.encode(req.getImages(), "评价", imageUrlUtil);

        if (existing != null) {
            // 覆盖旧评价（与 PUT /reviews/{id} 同语义，差异化仅在归属由 token 锁定、无需传评价 ID）
            reviewPersister.updateAndPublish(existing.getId(), req.getRating(), filteredContent, imagesJson, dishId);
            return existing.getId();
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setDishId(dishId);
        review.setRating(req.getRating());
        review.setContent(filteredContent);
        review.setIsHidden(0);
        review.setImages(imagesJson);

        // 落库 + 发布重算事件，一并收窄为单一事务（机审已在无事务状态下完成）
        reviewPersister.insertAndPublish(review, dishId, req.getRating());
        return review.getId();
    }

    /**
     * 重新评价（覆盖式）：覆盖同一行（评分/文字/配图），不新建行。
     * <p>
     * 覆盖语义（2026-09-20 拍板 D4）：created_at 刷新为当前（时间倒序下置顶）、is_hidden 重置 0、
     * 内容安全检测与首次发表同口径（违规 400 且原内容不变）、发既有 ReviewSubmittedEvent 重算聚合。
     * 鉴权 = 作者本人（非本人 403）；未认证由 Controller 的 @RequireVerified 给出 4031。
     */
    /**
     * 重新评价。<b>本方法刻意不加 {@code @Transactional}</b>：与 {@link #submitReview} 同理，
     * 归属校验与机审均在无事务状态下完成，仅落库一步开事务。
     */
    @Override
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
        String filteredContent = localSensitiveFilter.filter(req.getContent());
        // 与首次发表同口径：认证 + openid 准入 + 文本走微信内容安全检测 msgSecCheck（图片已在 /upload/cloud-image 链路过 imgSecCheck）
        UserAuthContextVO reviewUser = requireUgcAuthorizedUser(userId);
        checkUgcText(reviewUser, filteredContent, 2);
        String imagesJson = UgcImageValidator.encode(req.getImages(), "评价", imageUrlUtil);

        // 覆盖同一行 + 发布重算事件，一并收窄为单一事务（机审已在无事务状态下完成）
        reviewPersister.updateAndPublish(id, req.getRating(), filteredContent, imagesJson, review.getDishId());
    }

    /**
     * UGC 作者准入：已认证（bind_email 非空，判据唯一真源在 auth，经
     * {@link UserService#getAuthContext(Long)} 折算为布尔值下发）且 openid 非空
     * （msgSecCheck v2 必填 openid）。
     * <p>
     * 判据与错误码与原实现逐字一致；差别仅在于不再返回 {@code auth.entity.User} 实体，
     * 而返回只含判定要素的跨域投影（P0-1：review 不再 import auth 实体/Mapper）。
     *
     * @return 通过准入校验的用户准入上下文
     */
    private UserAuthContextVO requireUgcAuthorizedUser(Long userId) {
        UserAuthContextVO user = userService.getAuthContext(userId);
        if (user == null || !user.isVerified()) {
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
    private void checkUgcText(UserAuthContextVO user, String content, int scene) {
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

    /** 本人视角版本（{@link MyReviewVO} 不继承 {@link ReviewVO}，故独立成法；转换口径与公开视角逐字一致） */
    private void fillMyReviewImages(List<MyReviewVO> records) {
        if (records == null) {
            return;
        }
        for (MyReviewVO vo : records) {
            List<String> images = vo.getImages();
            vo.setImages(images == null || images.isEmpty() ? List.of() : imageUrlUtil.toAbsoluteUrls(images));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id, Long userId) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            // 4001 = 资源不存在（docs/client/feature/client-删除本人评价.md）：端上据此给出恢复路径，不解析 message
            throw new BusinessException(4001, "评价不存在");
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
        // 作者昵称/头像经 auth 域只读契约下发、菜品名经 dish 域只读契约下发（P0-1：
        // review 不再注入 UserMapper/DishMapper，也不再 import 他域实体）
        Map<Long, UserBriefVO> userMap = userService.mapBriefByIds(userIds);
        Map<Long, String> dishNameMap = dishService.mapNameByIds(dishIds);
        List<ReviewAdminVO> vos = new ArrayList<>(records.size());
        for (Review r : records) {
            ReviewAdminVO vo = toAdminVO(r);
            UserBriefVO u = r.getUserId() == null ? null : userMap.get(r.getUserId());
            vo.setUserNickname(u != null ? u.getNickname() : null);
            // 头像已在 auth 侧完成相对路径 → 绝对 URL 转换，本处不再二次加工
            vo.setUserAvatar(u != null ? u.getAvatarUrl() : null);
            vo.setDishName(r.getDishId() == null ? null : dishNameMap.get(r.getDishId()));
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

    // ==================== 跨域写契约实现（P0-1：由本域 event 监听器调用） ====================

    /**
     * 菜品删除级联清理（原实现为 {@code DishServiceImpl.deleteDish} 内的
     * {@code reviewMapper.delete(...)}，SQL 与语义逐字保留，仅换调用方）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByDishId(Long dishId) {
        if (dishId == null) {
            return 0;
        }
        return reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getDishId, dishId));
    }

    /**
     * 账号归属迁移（原实现为 {@code AuthServiceImpl.migrateOwnership} 中的两条 review 语句，
     * 「先清冲突行、再改归属」的顺序与 SQL 逐字保留）。
     * <p>
     * 不发布 ReviewSubmittedEvent：迁移是归属改写、不改评分，且批量重算聚合会放大写放大，
     * 与原实现一致（原实现同样不触发重算）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateOwnership(Long fromUserId, Long toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return 0;
        }
        // 唯一键 uk_review_user_dish：若新账号已对该 dish 有评价，删除旧账号同 dish 评价（保留新账号）
        reviewMapper.delete(new LambdaUpdateWrapper<Review>()
                .eq(Review::getUserId, fromUserId)
                .inSql(Review::getDishId, "SELECT dish_id FROM review WHERE user_id = " + toUserId));
        return reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getUserId, fromUserId)
                .set(Review::getUserId, toUserId));
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
