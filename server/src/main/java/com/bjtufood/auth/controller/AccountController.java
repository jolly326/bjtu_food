package com.bjtufood.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.config.TokenBlacklist;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.annotation.AuditLog;
import com.bjtufood.common.constant.OperationLogConst;
import com.bjtufood.common.result.Result;
import com.bjtufood.common.utils.SecurityUtil;
import com.bjtufood.apply.entity.ApplyAction;
import com.bjtufood.apply.mapper.ApplyActionMapper;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.feedback.mapper.FeedbackMapper;
import com.bjtufood.history.entity.ViewLog;
import com.bjtufood.history.mapper.ViewLogMapper;
import com.bjtufood.moment.entity.Moment;
import com.bjtufood.moment.entity.MomentComment;
import com.bjtufood.moment.entity.MomentCommentUseful;
import com.bjtufood.moment.entity.MomentUseful;
import com.bjtufood.moment.mapper.MomentCommentMapper;
import com.bjtufood.moment.mapper.MomentCommentUsefulMapper;
import com.bjtufood.moment.mapper.MomentMapper;
import com.bjtufood.moment.mapper.MomentUsefulMapper;
import com.bjtufood.notify.entity.Notification;
import com.bjtufood.notify.mapper.NotificationMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.entity.ReviewUseful;
import com.bjtufood.review.mapper.ReviewMapper;
import com.bjtufood.review.mapper.ReviewUsefulMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账号注销（task-12.8，Q4-①）
 * <p>
 * 逻辑删除 user.status='deleted'，级联清理本人 dish/moment(+comment)/review/notification/user_feedback，
 * 并将当前 token 加入黑名单立即失效。
 */
@Tag(name = "17. 账号注销", description = "学生注销本人账号（二次确认由前端完成）。STU。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AccountController {

    private final UserMapper userMapper;
    private final DishMapper dishMapper;
    private final MomentMapper momentMapper;
    private final MomentCommentMapper momentCommentMapper;
    private final ReviewMapper reviewMapper;
    private final NotificationMapper notificationMapper;
    private final FeedbackMapper feedbackMapper;
    private final ApplyActionMapper applyActionMapper;
    private final ReviewUsefulMapper reviewUsefulMapper;
    private final MomentUsefulMapper momentUsefulMapper;
    private final MomentCommentUsefulMapper momentCommentUsefulMapper;
    private final ViewLogMapper viewLogMapper;
    private final TokenBlacklist tokenBlacklist;

    @Data
    static class AccountDeleteReq {
        /** 二次确认标记（前端二次确认后传 true） */
        private Boolean confirm;
    }

    @Operation(
            summary = "注销本人账号",
            description = "STU。逻辑删除本人账号并级联清理本人数据，当前 token 立即失效。建议前端做二次确认。",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
                    { "confirm": true }
                    """)))
    )
    @AuditLog(action = OperationLogConst.ACTION_ACCOUNT_DELETE, targetType = "user", targetId = "#userId")
    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/my/account")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAccount(@RequestBody(required = false) AccountDeleteReq req,
                                       HttpServletRequest httpRequest) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 二次确认：显式 confirm=true 才执行（避免误触）
        if (req == null || !Boolean.TRUE.equals(req.getConfirm())) {
            return Result.badRequest("请确认注销");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        cascadeClean(userId);

        // 逻辑删除（与 active/disabled 并存），避免外键悬空
        user.setStatus("deleted");
        userMapper.updateById(user);

        // 当前 token 立即失效
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            tokenBlacklist.revoke(authHeader.substring(7).trim());
        }
        // 同时按用户维度拉黑：本人在其他设备上已签发的 token 一并失效
        tokenBlacklist.revokeUser(userId);

        return Result.success();
    }

    /**
     * 级联清理本人数据（task-12.8）：dish / moment(+comment+useful) / review(+useful) /
     * notification / user_feedback / apply_action / view_log。
     * favorite 模块已整体移除（task-12.12），无需清理。
     */
    private void cascadeClean(Long userId) {
        // 动态：先清本人动态的评论及其 useful、动态 useful，再清动态
        List<Moment> moments = momentMapper.selectList(
                new LambdaQueryWrapper<Moment>().eq(Moment::getUserId, userId));
        List<Long> momentIds = moments.stream().map(Moment::getId).toList();
        if (!momentIds.isEmpty()) {
            momentCommentMapper.delete(new LambdaQueryWrapper<MomentComment>().in(MomentComment::getMomentId, momentIds));
            momentUsefulMapper.delete(new LambdaQueryWrapper<MomentUseful>().in(MomentUseful::getMomentId, momentIds));
        }
        momentMapper.delete(new LambdaQueryWrapper<Moment>().eq(Moment::getUserId, userId));

        // 本人评论的 useful 标记
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId));
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        if (!reviewIds.isEmpty()) {
            reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>().in(ReviewUseful::getReviewId, reviewIds));
        }
        // 本人发出的 useful 标记（对他人内容的点赞/有用）
        reviewUsefulMapper.delete(new LambdaQueryWrapper<ReviewUseful>().eq(ReviewUseful::getUserId, userId));
        momentUsefulMapper.delete(new LambdaQueryWrapper<MomentUseful>().eq(MomentUseful::getUserId, userId));
        momentCommentUsefulMapper.delete(new LambdaQueryWrapper<MomentCommentUseful>().eq(MomentCommentUseful::getUserId, userId));

        dishMapper.delete(new LambdaQueryWrapper<Dish>().eq(Dish::getCreatedBy, userId));
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId));
        feedbackMapper.delete(new LambdaQueryWrapper<Feedback>().eq(Feedback::getUserId, userId));
        // 本人提交的实体申请（新增/下架/变更）
        applyActionMapper.delete(new LambdaQueryWrapper<ApplyAction>().eq(ApplyAction::getApplicantId, userId));
        // 本人浏览足迹
        viewLogMapper.delete(new LambdaQueryWrapper<ViewLog>().eq(ViewLog::getUserId, userId));
    }
}
