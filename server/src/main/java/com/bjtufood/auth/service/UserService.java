package com.bjtufood.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.auth.dto.UserAuthContextVO;
import com.bjtufood.auth.dto.UserBriefVO;
import com.bjtufood.auth.dto.UserOverviewVO;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;

import java.util.Collection;
import java.util.Map;

/**
 * 用户管理服务接口
 * <p>
 * 供系统管理员操作，位于 auth 模块中。
 * 管理用户的状态，不依赖其他模块。
 * <p>
 * <b>下半区的「跨域只读契约」是分层收口点</b>：review / feedback / correction / 切面等
 * **一律消费本接口的方法与 auth 自有 DTO**，不直接注入 {@code UserMapper}、
 * 不读 {@code auth.entity.User}（跨域实体 + 绕过 UserVO 的 isVerified 派生 getter 属分层违规）。
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
    /**
     * 管理端用户列表（分页）。
     *
     * @param status  状态筛选（`active`/`disabled`/`deleted`；不传 = 全部）
     * @param keyword 关键词（**昵称 / 账号 / 绑定邮箱**模糊匹配，便于按人定位；可空）
     */
    IPage<UserVO> listUsers(int page, int pageSize, String status, String keyword);

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
     * 启用/禁用用户账号（{@code PUT /admin/users/{id}/status}）。
     * <p>
     * 只改 {@code status}（字符串枚举，取值域仅 {@code active} / {@code disabled}）；禁用时拉黑该用户
     * 已签发的全部 token（立即失效），恢复 {@code active} 时解除拉黑。
     *
     * @param id     用户ID
     * @param status 目标状态（active/disabled）
     * @throws com.bjtufood.common.exception.BusinessException code=400 状态非法；
     *         code=4001 用户不存在
     */
    void updateStatus(Long id, String status);

    // ==================== 跨域只读契约（P0-1 分层约束） ====================

    /**
     * UGC 准入判定（供 {@code auth.aspect.RequireVerifiedAspect} 消费）。
     * <p>
     * 判据与错误码固定为（401 未登录 / 403 账号非 active / 4031 未认证），
     * 使「切面拦截」与「提交评价时的前置校验」保持同码同语义；判定逻辑收敛到 auth 域本方法一处。
     *
     * @param userId 当前登录用户ID（可空：未登录直接 401）
     * @throws com.bjtufood.common.exception.BusinessException 401 / 403 / 4031
     */
    void requireUgcAuthorized(Long userId);

    /**
     * 取 UGC 准入上下文（是否存在 + 认证态 + openid），供 review 域提交/重评前置校验。
     *
     * @param userId 用户ID
     * @return 准入上下文；用户不存在返回 null
     */
    UserAuthContextVO getAuthContext(Long userId);

    /**
     * 是否已邮箱认证（管理端回执投递判据：仅对已认证用户投递）。
     *
     * @param userId 用户ID（可空，空值返回 false）
     * @return true=已认证；用户不存在亦为 false
     */
    boolean isVerifiedById(Long userId);

    /**
     * 批量取昵称（管理端列表补齐「提交人」用，一次 IN 查询消除 N+1）。
     *
     * @param userIds 用户ID集合（null/空集合返回空 Map，不发起查询）
     * @return userId → nickname 映射（昵称缺失的账号不入图，与旧实现 put(null) 后 VO 留空等价）
     */
    Map<Long, String> mapNicknameByIds(Collection<Long> userIds);

    /**
     * 批量取作者展示快照（昵称 + 头像绝对 URL），供 review 管理端列表补齐。
     *
     * @param userIds 用户ID集合（null/空集合返回空 Map，不发起查询）
     * @return userId → {@link UserBriefVO} 映射
     */
    Map<Long, UserBriefVO> mapBriefByIds(Collection<Long> userIds);

    /**
     * 用户规模计数（D1 看板概况）：总数（**不含已注销**）+ 已认证数（`bind_email` 非空）。
     *
     * @return 用户规模计数
     */
    UserOverviewVO countOverview();
}
