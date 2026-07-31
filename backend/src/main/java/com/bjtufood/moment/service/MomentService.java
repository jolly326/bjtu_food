package com.bjtufood.moment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.moment.dto.MomentCommentReq;
import com.bjtufood.moment.dto.MomentCommentVO;
import com.bjtufood.moment.dto.MomentPublishReq;
import com.bjtufood.moment.dto.MomentUsefulResult;
import com.bjtufood.moment.dto.MomentVO;

/**
 * 社区动态服务接口
 */
public interface MomentService {

    /**
     * 公开广场列表（仅 approved + status=0），支持 dishId/stallId 关联过滤，分页
     */
    IPage<MomentVO> publicList(String tab, Long dishId, Long stallId, Long canteenId, int page, int pageSize);

    /**
     * 动态详情（作者本人可见 rejectReason）
     */
    MomentVO detail(Long id, Long currentUserId);

    /**
     * 发布动态（STU，audit_status=pending）
     */
    Long publish(Long userId, MomentPublishReq req);

    /**
     * 我的动态列表（按当前用户 + 可选审核态过滤）
     */
    java.util.List<MomentVO> myMoments(Long userId, String auditStatus);

    /**
     * 编辑重提（仅作者，复用原记录，pending + 清空 rejectReason）
     */
    void updateMoment(Long id, Long userId, MomentPublishReq req);

    /**
     * 删除自己动态（物理删除，连带评论、通知）
     */
    void deleteMoment(Long id, Long userId);

    /**
     * 👍 有用切换（幂等，一人一票）
     */
    MomentUsefulResult toggleUseful(Long momentId, Long userId);

    /**
     * 发评论（一层回复），返回评论ID
     */
    Long comment(Long momentId, Long userId, MomentCommentReq req);

    /**
     * 评论 👍 有用切换（幂等，一人一票；task-12.4）
     *
     * @return 是否点过 + 当前计数
     */
    com.bjtufood.moment.dto.MomentUsefulResult toggleCommentUseful(Long momentId, Long commentId, Long userId);

    /**
     * 评论列表（按 created_at asc，扁平化带 parentId/replyToNickname）
     */
    IPage<MomentCommentVO> commentList(Long momentId, Long currentUserId, int page, int pageSize);

    /**
     * 删除自己评论（连带子回复，commentCount-1）
     */
    void deleteComment(Long momentId, Long commentId, Long userId);

    /**
     * 审核通过（复用状态机，写通知）
     */
    void approve(Long id);

    /**
     * 审核退回（复用状态机，写通知）
     */
    void reject(Long id, String rejectReason);

    /**
     * 后台动态管理列表（含全部状态：approved+status=0、已下架 status=1、pending 等），支持 status/auditStatus 过滤
     */
    IPage<MomentVO> adminList(Integer status, String auditStatus, int page, int pageSize);

    /**
     * 强制下架（status=1）
     */
    void hide(Long id);

    /**
     * 物理删除（admin，连带评论、通知）
     */
    void adminDelete(Long id);
}
