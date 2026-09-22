package com.bjtufood.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;

/**
 * 用户管理服务接口
 * <p>
 * 供系统管理员操作，位于 auth 模块中。
 * 管理用户的状态，不依赖其他模块（角色筛选/角色管理已随 user.role 列退役移除，2026-09-15）。
 */
public interface UserService {

    /**
     * 分页查询用户列表（管理端用）
     * <p>
     * 支持按状态筛选，结果不返回密码字段
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   状态筛选（可选）
     * @return 分页用户列表
     */
    IPage<UserVO> listUsers(int page, int pageSize, String status);

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱
     * @return 用户实体，不存在返回 null
     */
    User getByEmail(String email);

    /**
     * 根据微信 openid 查询用户（微信静默登录取号）。
     *
     * @param openid 微信 openid
     * @return 用户实体，不存在返回 null
     */
    User getByOpenid(String openid);

    /**
     * 新建微信游客账号（游客态 = bind_email 为 NULL；认证态无布尔列，判据见 AuthStateUtil）。
     * <p>
     * <b>语义</b>：建号是「INSERT 占位昵称 + 按自增 id 回填最终昵称」两步写库，必须原子完成——
     * 任一步失败都不得留下昵称为占位值的账号。因此本方法声明为**独立 Bean 上的事务方法**
     * （而非在调用方 {@code AuthServiceImpl} 内的同类私有方法上标注：自调用绕过代理，事务不生效），
     * 同时使远程 {@code code2Session} 调用留在事务之外。
     *
     * @param openid 微信 openid（已由 {@code code2Session} 换取）
     * @return 新建的游客账号；若并发下已被同 openid 抢先建号则返回既有账号
     */
    User createWechatGuest(String openid);

    /**
     * 根据已认证绑定邮箱查询用户（bind_email 唯一认证绑定）。
     *
     * @param bindEmail 认证绑定邮箱
     * @return 用户实体，不存在返回 null
     */
    User getByBindEmail(String bindEmail);

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

}
