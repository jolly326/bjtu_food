package com.bjtufood.content.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.content.dto.AuditVO;

/**
 * UGC 审核服务接口
 * <p>
 * 学生提交的菜品/档口/食堂基础信息均 audit_status=pending → 管理员 approved/rejected。
 * 小程序端只展示 approved 且上架/营业中；评价用 is_hidden 控制可见性。
 */
public interface AuditService {

    /**
     * 审核列表（分页）
     * <p>
     * 支持按 type（dish/stall/canteen）与 status（pending/approved/rejected）过滤。
     *
     * @param type     审核对象类型：dish / stall / canteen
     * @param status   审核状态（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页审核视图列表
     */
    IPage<AuditVO> listAudit(String type, String status, int page, int pageSize);

    /**
     * 审核通过：置 audit_status=approved 入正式体系
     *
     * @param type 审核对象类型
     * @param id   记录ID
     */
    void approve(String type, Long id);

    /**
     * 审核退回：置 audit_status=rejected 并回写 reject_reason（必填）
     *
     * @param type         审核对象类型
     * @param id           记录ID
     * @param rejectReason 退回原因（必填）
     */
    void reject(String type, Long id, String rejectReason);
}
