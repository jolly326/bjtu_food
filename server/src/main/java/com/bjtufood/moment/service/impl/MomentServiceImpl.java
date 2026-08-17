package com.bjtufood.moment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.common.utils.JsonListUtil;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.moment.constant.MomentConst;
import com.bjtufood.moment.dto.MomentCommentReq;
import com.bjtufood.moment.dto.MomentCommentVO;
import com.bjtufood.moment.dto.MomentPublishReq;
import com.bjtufood.moment.dto.MomentUsefulResult;
import com.bjtufood.moment.dto.MomentVO;
import com.bjtufood.moment.entity.Moment;
import com.bjtufood.moment.entity.MomentUseful;
import com.bjtufood.moment.entity.MomentComment;
import com.bjtufood.moment.entity.MomentCommentUseful;
import com.bjtufood.moment.mapper.MomentCommentMapper;
import com.bjtufood.moment.mapper.MomentCommentUsefulMapper;
import com.bjtufood.moment.mapper.MomentMapper;
import com.bjtufood.moment.mapper.MomentUsefulMapper;
import com.bjtufood.moment.service.MomentService;
import com.bjtufood.notify.constant.NotificationConst;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.mapper.NotificationMapper;
import com.bjtufood.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区动态服务实现
 */
@Service
@RequiredArgsConstructor
public class MomentServiceImpl implements MomentService {

    private final MomentMapper momentMapper;
    private final MomentCommentMapper momentCommentMapper;
    private final MomentUsefulMapper momentUsefulMapper;
    private final MomentCommentUsefulMapper momentCommentUsefulMapper;
    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final StallMapper stallMapper;
    private final CanteenMapper canteenMapper;
    private final NotificationService notificationService;
    private final ImageUrlUtil imageUrlUtil;

    @Override
    public IPage<MomentVO> publicList(String tab, Long dishId, Long stallId, Long canteenId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;
        // recommend 暂等价 latest（三期关注流预留参数位），均按 created_at desc
        IPage<MomentVO> result = momentMapper.selectPublicPage(new Page<>(page, pageSize), dishId, stallId, canteenId);
        result.setRecords(result.getRecords().stream().map(this::enrich).toList());
        return result;
    }

    @Override
    public MomentVO detail(Long id, Long currentUserId) {
        Moment m = momentMapper.selectById(id);
        // 不存在或已下架（管理员强制下架 status=1）均按「不可见」处理，返回 404 业务码，避免暴露数据/状态
        if (m == null) {
            throw new BusinessException(404, "动态不存在或已下架");
        }
        int status = m.getStatus() == null ? -1 : m.getStatus();
        if (status != MomentConst.STATUS_NORMAL) {
            throw new BusinessException(404, "动态不存在或已下架");
        }
        // 作者本人可见（含 pending/rejected），非作者仅可看 approved 动态
        boolean isAuthor = currentUserId != null && currentUserId.equals(m.getUserId());
        if (!isAuthor && !MomentConst.AUDIT_APPROVED.equals(m.getAuditStatus())) {
            throw new BusinessException(404, "动态不存在或已下架");
        }
        MomentVO vo = toVO(m);
        // 作者本人可见 rejectReason
        if (isAuthor) {
            vo.setRejectReason(m.getRejectReason());
        } else {
            vo.setRejectReason(null);
        }
        // 当前用户是否已点「有用」：发评论/刷新重载时用于回写高亮态，避免被归零
        if (currentUserId != null) {
            long usefulHit = momentUsefulMapper.selectCount(new LambdaQueryWrapper<MomentUseful>()
                .eq(MomentUseful::getUserId, currentUserId)
                .eq(MomentUseful::getMomentId, id));
            vo.setUseful(usefulHit > 0);
        }
        return enrich(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long userId, MomentPublishReq req) {
        validateRelated(req);
        Moment m = new Moment();
        applyReq(m, req);
        m.setUserId(userId);
        m.setAuditStatus(MomentConst.AUDIT_PENDING);
        m.setRejectReason(null);
        m.setUsefulCount(0);
        m.setCommentCount(0);
        m.setStatus(MomentConst.STATUS_NORMAL);
        momentMapper.insert(m);
        return m.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishFromReview(Long userId, String content, List<String> images, Long dishId) {
        // 评价与动态打通：评价可见即动态可见（approved 直接上广场，无需后台审核）
        if (dishId == null) {
            throw new BusinessException("评价同步动态需要关联菜品");
        }
        Moment m = new Moment();
        m.setUserId(userId);
        m.setContent(content);
        m.setImages(JsonListUtil.toJson(images));
        m.setRelatedType(MomentConst.RELATED_DISH);
        m.setRelatedId(dishId);
        m.setAuditStatus(MomentConst.AUDIT_APPROVED);
        m.setRejectReason(null);
        m.setUsefulCount(0);
        m.setCommentCount(0);
        m.setStatus(MomentConst.STATUS_NORMAL);
        momentMapper.insert(m);
        return m.getId();
    }

    @Override
    public List<MomentVO> myMoments(Long userId, String auditStatus) {
        List<MomentVO> list = momentMapper.selectMyMoments(userId, auditStatus);
        return list.stream().map(this::enrich).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMoment(Long id, Long userId, MomentPublishReq req) {
        Moment m = momentMapper.selectById(id);
        if (m == null || !Objects.equals(m.getUserId(), userId)) {
            throw new BusinessException("动态不存在");
        }
        validateRelated(req);
        applyReq(m, req);
        // 编辑重提：复用原记录，审核态回到 pending，退回原因清空
        m.setAuditStatus(MomentConst.AUDIT_PENDING);
        m.setRejectReason(null);
        momentMapper.updateById(m);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMoment(Long id, Long userId) {
        Moment m = momentMapper.selectById(id);
        if (m == null || !Objects.equals(m.getUserId(), userId)) {
            throw new BusinessException("动态不存在");
        }
        // 连带评论、通知清理
        momentCommentMapper.delete(new LambdaQueryWrapper<MomentComment>().eq(MomentComment::getMomentId, id));
        cleanupNotification(id);
        momentMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MomentUsefulResult toggleUseful(Long momentId, Long userId) {
        Moment m = momentMapper.selectById(momentId);
        if (m == null) {
            throw new BusinessException("动态不存在");
        }
        // 仅对外可见（approved + 正常）的动态允许点赞
        if (!MomentConst.AUDIT_APPROVED.equals(m.getAuditStatus())
                || m.getStatus() == null || m.getStatus() != MomentConst.STATUS_NORMAL) {
            throw new BusinessException("动态不可操作");
        }
        LambdaQueryWrapper<MomentUseful> w = new LambdaQueryWrapper<MomentUseful>()
                .eq(MomentUseful::getUserId, userId)
                .eq(MomentUseful::getMomentId, momentId);
        MomentUseful exist = momentUsefulMapper.selectOne(w);
        MomentUsefulResult result = new MomentUsefulResult();
        if (exist != null) {
            momentUsefulMapper.deleteById(exist.getId());
            // 计数原子 -1（并发安全）
            momentMapper.changeUsefulCount(momentId, -1);
            result.setUseful(false);
        } else {
            MomentUseful useful = new MomentUseful();
            useful.setUserId(userId);
            useful.setMomentId(momentId);
            momentUsefulMapper.insert(useful);
            // 计数原子 +1（并发安全）
            momentMapper.changeUsefulCount(momentId, 1);
            result.setUseful(true);
            // 被赞通知（作者本人被赞才通知，避免自赞）
            if (!userId.equals(m.getUserId())) {
                sendUsefulNotification(m);
            }
        }
        // 原子增减后回读最新计数
        Moment latest = momentMapper.selectById(momentId);
        result.setUsefulCount(latest == null ? 0 : (latest.getUsefulCount() == null ? 0 : latest.getUsefulCount()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long comment(Long momentId, Long userId, MomentCommentReq req) {
        Moment m = momentMapper.selectById(momentId);
        if (m == null) {
            throw new BusinessException("动态不存在");
        }
        // content/images 至少一项（支持纯图评论）
        boolean hasContent = req.getContent() != null && !req.getContent().trim().isEmpty();
        List<String> reqImages = req.getImages();
        boolean hasImages = reqImages != null && !reqImages.isEmpty();
        if (!hasContent && !hasImages) {
            throw new BusinessException("评论内容或图片至少填写一项");
        }
        MomentComment c = new MomentComment();
        c.setMomentId(momentId);
        c.setUserId(userId);
        c.setParentId(req.getParentId());
        c.setContent(req.getContent());
        // 评论图片：最多 3 张，按 JSON 数组字符串存储（与 Dish.images 一致）
        if (reqImages != null && !reqImages.isEmpty()) {
            List<String> safe = reqImages.stream().limit(3).collect(Collectors.toList());
            c.setImages(JsonListUtil.toJson(safe));
        }
        momentCommentMapper.insert(c);

        // 评论计数原子 +1（并发安全）
        momentMapper.changeCommentCount(momentId, 1);

        // 回复他人 → 给被回复者发 comment 通知（不通知自己，且被回复者应是动态作者或评论者）
        if (req.getParentId() != null) {
            MomentComment parent = momentCommentMapper.selectById(req.getParentId());
            if (parent != null && !parent.getUserId().equals(userId)) {
                sendCommentNotification(m, parent.getUserId());
            }
        }
        return c.getId();
    }

    @Override
    public IPage<MomentCommentVO> commentList(Long momentId, Long currentUserId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        IPage<MomentComment> p = momentCommentMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<MomentComment>()
                        .eq(MomentComment::getMomentId, momentId)
                        .orderByAsc(MomentComment::getCreatedAt));
        List<Long> userIds = p.getRecords().stream()
                .map(MomentComment::getUserId)
                .distinct()
                .toList();
        List<Long> parentIds = p.getRecords().stream()
                .map(MomentComment::getParentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, User> userMap = loadUsers(userIds);
        Map<Long, User> parentMap = loadUsers(parentIds);

        // 当前用户对评论的 👍 标记状态（task-12.4）
        // 注意：动态无评论时 commentIds 为空集合，直接 .in(空) 会生成非法 SQL "IN ()" 触发 500
        // （项目已知坑，同 AuditServiceImpl），故需先做空集合防护。
        final Set<Long> markedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            List<Long> commentIds = p.getRecords().stream().map(MomentComment::getId).toList();
            if (!commentIds.isEmpty()) {
                markedCommentIds.addAll(momentCommentUsefulMapper.selectList(new LambdaQueryWrapper<MomentCommentUseful>()
                                .eq(MomentCommentUseful::getUserId, currentUserId)
                                .in(MomentCommentUseful::getCommentId, commentIds))
                        .stream().map(MomentCommentUseful::getCommentId).collect(Collectors.toSet()));
            }
        }

        IPage<MomentCommentVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream().map(c -> {
            MomentCommentVO vo = new MomentCommentVO();
            vo.setId(c.getId());
            vo.setMomentId(c.getMomentId());
            vo.setUserId(c.getUserId());
            User u = userMap.get(c.getUserId());
            vo.setUserNickname(u != null ? u.getNickname() : null);
            vo.setUserAvatar(u != null ? imageUrlUtil.toAbsoluteUrl(u.getAvatar()) : null);
            vo.setParentId(c.getParentId());
            if (c.getParentId() != null) {
                User pu = parentMap.get(c.getParentId());
                vo.setReplyToNickname(pu != null ? pu.getNickname() : null);
            }
            vo.setContent(c.getContent());
            // 评论图片：JSON 数组字符串 → 绝对 URL 列表（最多 3 张）
            vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(c.getImages()));
            vo.setUsefulCount(c.getUsefulCount() == null ? 0 : c.getUsefulCount());
            vo.setUseful(markedCommentIds.contains(c.getId()));
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MomentUsefulResult toggleCommentUseful(Long momentId, Long commentId, Long userId) {
        MomentComment c = momentCommentMapper.selectById(commentId);
        if (c == null || !c.getMomentId().equals(momentId)) {
            throw new BusinessException("评论不存在");
        }
        LambdaQueryWrapper<MomentCommentUseful> w = new LambdaQueryWrapper<MomentCommentUseful>()
                .eq(MomentCommentUseful::getUserId, userId)
                .eq(MomentCommentUseful::getCommentId, commentId);
        MomentCommentUseful exist = momentCommentUsefulMapper.selectOne(w);
        MomentUsefulResult result = new MomentUsefulResult();
        if (exist != null) {
            momentCommentUsefulMapper.deleteById(exist.getId());
            // 计数原子 -1（并发安全）
            momentCommentMapper.changeUsefulCount(commentId, -1);
            result.setUseful(false);
        } else {
            MomentCommentUseful useful = new MomentCommentUseful();
            useful.setUserId(userId);
            useful.setCommentId(commentId);
            momentCommentUsefulMapper.insert(useful);
            // 计数原子 +1（并发安全）
            momentCommentMapper.changeUsefulCount(commentId, 1);
            result.setUseful(true);
            // 被赞通知（非自赞）
            if (!userId.equals(c.getUserId())) {
                sendCommentUsefulNotification(c);
            }
        }
        // 原子增减后回读最新计数
        MomentComment latest = momentCommentMapper.selectById(commentId);
        result.setUsefulCount(latest == null ? 0 : (latest.getUsefulCount() == null ? 0 : latest.getUsefulCount()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long momentId, Long commentId, Long userId) {
        MomentComment c = momentCommentMapper.selectById(commentId);
        if (c == null || !c.getMomentId().equals(momentId)) {
            throw new BusinessException("评论不存在");
        }
        if (!c.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }
        // 连带子回复删除
        List<MomentComment> children = momentCommentMapper.selectList(
                new LambdaQueryWrapper<MomentComment>().eq(MomentComment::getParentId, commentId));
        int removed = 1 + children.size();
        momentCommentMapper.delete(new LambdaQueryWrapper<MomentComment>()
                .and(w -> w.eq(MomentComment::getId, commentId)
                        .or().eq(MomentComment::getParentId, commentId)));

        // 评论数原子批量减少（并发安全）
        momentMapper.changeCommentCount(momentId, -removed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) throw new BusinessException("动态不存在");
        m.setAuditStatus(MomentConst.AUDIT_APPROVED);
        m.setRejectReason(null);
        momentMapper.updateById(m);
        sendAuditNotification(m, true, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String rejectReason) {
        if (!StringUtils.hasText(rejectReason)) {
            throw new BusinessException("退回原因不能为空");
        }
        Moment m = momentMapper.selectById(id);
        if (m == null) throw new BusinessException("动态不存在");
        m.setAuditStatus(MomentConst.AUDIT_REJECTED);
        m.setRejectReason(rejectReason);
        momentMapper.updateById(m);
        sendAuditNotification(m, false, rejectReason);
    }

    @Override
    public IPage<MomentVO> adminList(Integer status, String auditStatus, Long userId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;
        LambdaQueryWrapper<Moment> w = new LambdaQueryWrapper<Moment>()
                .orderByDesc(Moment::getCreatedAt);
        // 下架状态过滤（0=正常 1=下架），仅当显式传入时生效
        if (status != null) {
            w.eq(Moment::getStatus, status);
        }
        // 审核状态过滤（pending/approved/rejected），仅当显式传入时生效
        if (StringUtils.hasText(auditStatus)) {
            w.eq(Moment::getAuditStatus, auditStatus);
        }
        // 发布用户过滤（用户行为聚合），仅当显式传入时生效
        if (userId != null) {
            w.eq(Moment::getUserId, userId);
        }
        IPage<Moment> p = momentMapper.selectPage(new Page<>(page, pageSize), w);
        IPage<MomentVO> result = new Page<>(page, pageSize, p.getTotal());
        result.setRecords(p.getRecords().stream()
                .map(m -> enrich(toVO(m)))
                .toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hide(Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) throw new BusinessException("动态不存在");
        m.setStatus(MomentConst.STATUS_HIDDEN);
        momentMapper.updateById(m);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) throw new BusinessException("动态不存在");
        momentCommentMapper.delete(new LambdaQueryWrapper<MomentComment>().eq(MomentComment::getMomentId, id));
        cleanupNotification(id);
        momentMapper.deleteById(id);
    }

    // ==================== 内部辅助 ====================

    private void validateRelated(MomentPublishReq req) {
        if (req.getRelatedType() == null) req.setRelatedType(MomentConst.RELATED_NONE);
        if (!MomentConst.RELATED_NONE.equals(req.getRelatedType())) {
            if (req.getRelatedId() == null) {
                throw new BusinessException("关联对象ID不能为空");
            }
            if (MomentConst.RELATED_DISH.equals(req.getRelatedType())) {
                if (dishMapper.selectById(req.getRelatedId()) == null) {
                    throw new BusinessException("关联菜品不存在");
                }
            } else if (MomentConst.RELATED_STALL.equals(req.getRelatedType())) {
                if (stallMapper.selectById(req.getRelatedId()) == null) {
                    throw new BusinessException("关联档口不存在");
                }
            }
        }
    }

    private void applyReq(Moment m, MomentPublishReq req) {
        m.setContent(req.getContent());
        m.setImages(JsonListUtil.toJson(req.getImages()));
        m.setRelatedType(req.getRelatedType() == null ? MomentConst.RELATED_NONE : req.getRelatedType());
        m.setRelatedId(req.getRelatedId());
    }

    private MomentVO toVO(Moment m) {
        MomentVO vo = new MomentVO();
        vo.setId(m.getId());
        vo.setUserId(m.getUserId());
        vo.setContent(m.getContent());
        vo.setImagesJson(m.getImages());
        vo.setRelatedType(m.getRelatedType());
        vo.setRelatedId(m.getRelatedId());
        vo.setAuditStatus(m.getAuditStatus());
        vo.setRejectReason(m.getRejectReason());
        vo.setUsefulCount(m.getUsefulCount());
        vo.setCommentCount(m.getCommentCount());
        vo.setStatus(m.getStatus());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    /**
     * 补齐发布者昵称/头像、图片、关联名
     */
    private MomentVO enrich(MomentVO vo) {
        if (vo == null) return null;
        User u = userMapper.selectById(vo.getUserId());
        vo.setUserNickname(u != null ? u.getNickname() : null);
        vo.setUserAvatar(u != null ? imageUrlUtil.toAbsoluteUrl(u.getAvatar()) : null);
        vo.setImages(imageUrlUtil.parseAndToAbsoluteUrls(vo.getImagesJson()));
        vo.setImagesJson(null);
        if (vo.getRelatedId() != null && !MomentConst.RELATED_NONE.equals(vo.getRelatedType())) {
            if (MomentConst.RELATED_DISH.equals(vo.getRelatedType())) {
                Dish d = dishMapper.selectById(vo.getRelatedId());
                vo.setRelatedName(d != null ? d.getName() : null);
                List<String> dishImgs = d != null ? imageUrlUtil.parseAndToAbsoluteUrls(d.getImages()) : null;
                vo.setRelatedImage(dishImgs != null && !dishImgs.isEmpty() ? dishImgs.get(0) : null);
            } else if (MomentConst.RELATED_STALL.equals(vo.getRelatedType())) {
                Stall s = stallMapper.selectById(vo.getRelatedId());
                vo.setRelatedName(s != null ? s.getName() : null);
                if (s != null && s.getCanteenId() != null) {
                    Canteen c = canteenMapper.selectById(s.getCanteenId());
                    vo.setRelatedCanteen(c != null ? c.getName() : null);
                }
            }
        }
        return vo;
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, ids))
                .forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private void cleanupNotification(Long momentId) {
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationConst.TYPE_MOMENT_AUDIT)
                .eq(Notification::getRelatedId, momentId));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationConst.TYPE_USEFUL)
                .eq(Notification::getRelatedId, momentId));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationConst.TYPE_COMMENT)
                .eq(Notification::getRelatedId, momentId));
    }

    private void sendAuditNotification(Moment m, boolean approved, String rejectReason) {
        Notification n = new Notification();
        n.setUserId(m.getUserId());
        n.setType(NotificationConst.TYPE_MOMENT_AUDIT);
        n.setRelatedId(m.getId());
        n.setIsRead(0);
        if (approved) {
            n.setTitle("动态审核通过");
            n.setContent("您发布的动态已通过审核，现在对外可见啦~");
        } else {
            n.setTitle("动态审核未通过");
            n.setContent("您的动态未通过审核：" + (rejectReason == null ? "" : rejectReason));
        }
        notificationService.notify(n);
    }

    private void sendUsefulNotification(Moment m) {
        Notification n = new Notification();
        n.setUserId(m.getUserId());
        n.setType(NotificationConst.TYPE_USEFUL);
        n.setRelatedId(m.getId());
        n.setTitle("动态被赞");
        n.setContent("有同学觉得您的动态「有用 👍」");
        n.setIsRead(0);
        notificationService.notify(n);
    }

    private void sendCommentNotification(Moment m, Long toUserId) {
        Notification n = new Notification();
        n.setUserId(toUserId);
        n.setType(NotificationConst.TYPE_COMMENT);
        n.setRelatedId(m.getId());
        n.setTitle("动态新评论");
        n.setContent("有同学回复了您的动态评论");
        n.setIsRead(0);
        notificationService.notify(n);
    }

    private void sendCommentUsefulNotification(MomentComment c) {
        Notification n = new Notification();
        n.setUserId(c.getUserId());
        n.setType(NotificationConst.TYPE_USEFUL);
        n.setRelatedId(c.getMomentId());
        n.setTitle("评论被赞");
        n.setContent("有同学觉得您的评论「有用 👍」");
        n.setIsRead(0);
        notificationService.notify(n);
    }
}
