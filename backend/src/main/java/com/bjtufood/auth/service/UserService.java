package com.bjtufood.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;

/**
 * 用户管理服务接口
 * <p>
 * 供系统管理员操作，位于 auth 模块中。
 * 管理用户的状态和角色，不依赖其他模块。
 */
public interface UserService {

    /**
     * 分页查询用户列表（管理端用）
     * <p>
     * 支持按角色、状态筛选，结果不返回密码字段
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param role     角色筛选（可选）
     * @param status   状态筛选（可选）
     * @return 分页用户列表
     */
    IPage<UserVO> listUsers(int page, int pageSize, String role, String status);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体，不存在返回 null
     */
    User getByUsername(String username);

    /**
     * 启用/禁用用户账号
     * <p>
     * disabled 状态的用户无法登录
     *
     * @param id     用户ID
     * @param status 目标状态（active/disabled）
     * @throws com.bjtufood.common.exception.BusinessException 用户不存在或已为当前状态
     */
    void updateStatus(Long id, String status);

    /**
     * 修改用户角色和绑定的档口
     * <p>
     * 当设置为 canteen_admin 时，stallId 不能为空
     *
     * @param id      用户ID
     * @param role    新角色
     * @param stallId 绑定的档口ID（可为空）
     * @throws com.bjtufood.common.exception.BusinessException 参数不合法
     */
    void updateRole(Long id, String role, Long stallId);
}
