package com.bjtufood.apply.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.apply.dto.ApplyHandleReq;
import com.bjtufood.apply.dto.ApplyReq;
import com.bjtufood.apply.dto.ApplyVO;
import com.bjtufood.apply.dto.SubmissionVO;

import java.util.List;

/**
 * 实体贡献统一申请服务（task-12.1）
 */
public interface ApplyService {

    /**
     * 学生统一提交申请（POST /my/apply）。
     * 同 (entityType, entityId, applyType) 已存在 pending 时抛 409。
     */
    Long submit(Long applicantId, ApplyReq req);

    /**
     * 我的申请列表（STU，GET /my/apply）。
     */
    List<ApplyVO> myApplies(Long applicantId, String status);

    /**
     * 「我的提交」聚合（STU，GET /my/submissions）：apply + moment 两标签。
     */
    List<SubmissionVO> mySubmissions(Long applicantId);

    /**
     * 审核列表（ADM，GET /admin/apply），支持 status/entityType/applyType 过滤。
     */
    IPage<ApplyVO> adminList(String status, String entityType, String applyType, int page, int pageSize);

    /**
     * 审核通过（ADM），触发副作用（新增写实体 / 下架置 off / 变更写回字段）。
     */
    void approve(Long id, Long adminId);

    /**
     * 审核退回（ADM），rejectReason 必填。
     */
    void reject(Long id, Long adminId, ApplyHandleReq req);
}
