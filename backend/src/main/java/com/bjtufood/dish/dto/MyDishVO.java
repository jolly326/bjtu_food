package com.bjtufood.dish.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我的发布」菜品视图对象
 * <p>
 * 返回学生本人提交的菜品及其审核状态（audit_status）与退回原因（reject_reason），
 * 供小程序「我的发布 / 审核状态」页展示。
 */
@Data
public class MyDishVO {

    private Long id;

    private String name;

    /** 价格（分），前端自行转换显示为元 */
    private Integer price;

    private String description;

    /** 数据库原始 images JSON 字符串，由 Service 层解析为 List */
    private String imagesJson;

    private List<String> images;

    private String tags;

    /** 审核状态：pending/approved/rejected */
    private String auditStatus;

    /** 退回原因（audit_status=rejected 时由后台填写） */
    private String rejectReason;

    private LocalDateTime createdAt;
}
