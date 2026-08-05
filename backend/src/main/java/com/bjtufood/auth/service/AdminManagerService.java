package com.bjtufood.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.auth.dto.AdminCreateReq;
import com.bjtufood.auth.dto.UserVO;

/**
 * 管理员账号管理服务接口（仅超级管理员可用）
 * <p>
 * 负责 ADMIN 角色的账号增删改禁，与「学生用户管理（UserAdminController）」区分。
 */
public interface AdminManagerService {

    /**
     * 管理员账号列表（role=admin）
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   状态筛选（可选）
     * @return 分页管理员列表
     */
    IPage<UserVO> listAdmins(int page, int pageSize, String status);

    /**
     * 新增管理员账号（设初始密码，role=admin）
     *
     * @param req 管理员信息
     * @return 新管理员ID
     */
    Long createAdmin(AdminCreateReq req);

    /**
     * 启用/禁用管理员账号
     *
     * @param id     管理员ID
     * @param status active / disabled
     */
    void updateStatus(Long id, String status);

    /**
     * 更新管理员基础信息（昵称 / 密码；密码为空则不修改）
     *
     * @param id       管理员ID
     * @param nickname 新昵称（可为空=不改）
     * @param password 新密码（可为空=不改）
     */
    void update(Long id, String nickname, String password);

    /**
     * 删除管理员账号
     *
     * @param id 管理员ID
     */
    void delete(Long id);
}
