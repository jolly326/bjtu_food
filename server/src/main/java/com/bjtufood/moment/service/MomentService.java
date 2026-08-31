package com.bjtufood.moment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.moment.dto.MomentCommentReq;

import java.util.List;
import com.bjtufood.moment.dto.MomentCommentVO;
import com.bjtufood.moment.dto.MomentPublishReq;
import com.bjtufood.moment.dto.MomentUsefulResult;
import com.bjtufood.moment.dto.MomentVO;

/**
 * 社区动态服务接口
 */
public interface MomentService {

    /**
     * 公开广场列表（仅 approved + status=0），支持 dishId/stallId/canteenId 关联过滤，分页。
     * tab 取值：latest（默认，created_at desc）/ hot（R2：(useful_count*2+comment_count) DESC, created_at DESC）。
     * 非法 tab（含历史 recommend）由 Controller 层回退为 latest。
     */
    IPage<MomentVO> publicList(String tab, Long dishId, Long stallId, Long canteenId, int page, int pageSize);

    /**
     * 热门排行榜（R3）：PUB 端点 GET /moments/ranking。
     * 仅 approved + status=0，按 R2 公式取前 limit，非分页裸 List。
     * dishId/stallId/canteenId 关联过滤可选。
     */
    List<MomentVO> getRanking(int limit, Long dishId, Long stallId, Long canteenId);

    /**
     * 动态详情（作者本人可见 rejectReason）
     */
    MomentVO detail(Long id, Long currentUserId);

    /**
     * 发布动态（STU，audit_status=pending）
     */
    Long publish(Long userId, MomentPublishReq req);

    /**
     * 评价同步发布的动态（评价与动态打通 task：评价可见即动态可见）
     * 直接 approved + status=0，关联 dish；评价无正文时由调用方跳过。
     */
    Long publishFromReview(Long userId, String content, java.util.List<String> images, Long dishId);

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
     * 后台动态管理列表（含全部状态：approved+status=0、已下架 status=1、pending 等），支持 status/auditStatus/userId 过滤
     *
     * @param keyword 内容关键词（可选，对 content 做模糊匹配）
     */
    IPage<MomentVO> adminList(Integer status, String auditStatus, Long userId, String keyword, int page, int pageSize);

    /**
     * 强制下架（status=1）
     */
    void hide(Long id);

    /**
     * 物理删除（admin，连带评论、通知）
     */
    void adminDelete(Long id);
}
