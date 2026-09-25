package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.auth.dto.UserVO;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.auth.service.UserService;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 建号占位昵称：`nickname` 列为 NOT NULL，须先写占位值，拿到自增 id 后再于同一事务内回填 */
    private static final String NICKNAME_PLACEHOLDER = "食客新友";

    private final UserMapper userMapper;
    private final ImageUrlUtil imageUrlUtil;
    private final com.bjtufood.auth.config.TokenBlacklist tokenBlacklist;

    @Override
    public IPage<UserVO> listUsers(int page, int pageSize, String status) {
        int[] p = com.bjtufood.common.utils.PageUtil.normalize(page, pageSize);
        page = p[0]; pageSize = p[1];
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(new Page<>(page, pageSize), wrapper).convert(this::toVO);
    }

    @Override
    public User getByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    @Override
    public User getByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
    }

    @Override
    public User getByBindEmail(String bindEmail) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getBindEmail, bindEmail));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createWechatGuest(String openid) {
        User user = new User();
        user.setOpenid(openid);
        // 游客建号：username = wx_+openid 尾 16 位（保证唯一且不含敏感完整 openid）
        String tail = openid.length() > 16 ? openid.substring(openid.length() - 16) : openid;
        user.setUsername("wx_" + tail);
        // 最终昵称含自增 id 尾 4 位，而本列 NOT NULL → 先写占位值，插入后回填（同事务）
        user.setNickname(NICKNAME_PLACEHOLDER);
        user.setStatus("active");
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下 openid 唯一键兜底：重新查询已被抢先创建的账号（本方法整体回滚，不留半成品行）
            User existed = getByOpenid(openid);
            if (existed != null) {
                return existed;
            }
            throw new BusinessException("微信登录创建账号失败，请重试");
        }
        // 回填前判等（D5）：仅当昵称仍为占位值时才回填，避免覆盖任何已存在的用户昵称
        User fresh = userMapper.selectById(user.getId());
        if (fresh != null && NICKNAME_PLACEHOLDER.equals(fresh.getNickname())) {
            String nickname = buildGuestNickname(user.getId());
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getNickname, nickname));
            fresh.setNickname(nickname);
        }
        return fresh != null ? fresh : user;
    }

    @Override
    public void updateStatus(Long id, String status) {
        // 枚举校验：本接口契约仅允许 active/disabled（对齐 AdminManagerServiceImpl.updateStatus），
        // 非法值（含 deleted）一律 400，避免垃圾值直接落库
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException("非法的状态：" + status);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("User not found");
        }
        // 说明：管理端已无登录与角色体系（2026-09-13 定型），用户状态变更不再做「禁止操作自身/越权」判定；
        // 管理端接口整体由 AdminTokenFilter 的口令校验保护。
        user.setStatus(status);
        userMapper.updateById(user);
        // 禁用后该用户已签发的 token 必须立即失效（否则改了状态仍能带旧 token 访问）；
        // 恢复 active 时解除拉黑，使其可正常登录使用。
        // （deleted 状态仅由微信账号合并流程在 AuthServiceImpl 内部写入，不经本接口）
        if ("disabled".equals(status)) {
            tokenBlacklist.revokeUser(id);
        } else {
            tokenBlacklist.restoreUser(id);
        }
    }

    // 垂直越权防护（checkAdminOperation / resolveOperatorRole）已随管理端角色体系一并移除：
    // 后台无登录、无角色、单一使用者，管理端接口由 AdminTokenFilter 口令校验统一保护。

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(imageUrlUtil.toAbsoluteUrl(user.getAvatar()));
        vo.setStatus(user.getStatus());
        // 是否绑定微信：由 openid 非空派生（不暴露 openid 明文），管理端用户列表消费
        vo.setWechatBound(user.getOpenid() != null);
        vo.setBindEmail(user.getBindEmail());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    /**
     * 游客默认昵称：食客 + ID 尾 4 位（id 不足 4 位时取全量）。
     * <p>
     * 「游客短标识」不再作为任何接口出参（2026-09-21 spec §7.32）：该值是 `id` 的纯派生，
     * 学生端与管理端各自按同一规则现算；此处仅用于**建号默认昵称**这一处服务端写入。
     */
    private String buildGuestNickname(Long userId) {
        String id = String.valueOf(userId);
        String tail = id.length() > 4 ? id.substring(id.length() - 4) : id;
        return "食客" + tail;
    }
}
