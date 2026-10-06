package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 个人资料落库（**最小化事务边界**）。
 * <p>
 * 边界：事务**只覆盖落库一步** —— <b>先机审（无事务、不占连接）→ 再落库（才开事务）</b>。
 * 若事务从方法入口开始，会横跨「微信内容安全检测」的外部 HTTP 外呼（昵称 scene=1，超时 5s），
 * 期间持续占用数据库连接；HikariCP 默认池仅 10 条，并发一高即被占满并拖垮只读请求。
 * <p>
 * 必须是**独立 Bean**：Spring 声明式事务靠<b>代理</b>生效，同一类内自调用<b>不经过代理 ⇒ 事务不会开启</b>。
 */
@Component
@RequiredArgsConstructor
public class AuthProfilePersister {

    private final UserMapper userMapper;

    /**
     * 按字段局部更新昵称 / 头像（事务边界仅包住这一次 update）。
     * <p>
     * 沿用 <b>LambdaUpdateWrapper 局部更新</b>而非整行覆盖：避免把 {@code bind_email} / {@code status}
     * 等列重新写回，与「邮箱认证写 bind_email」等并发写操作产生 lost update。
     *
     * @param userId   目标用户
     * @param nickname 新昵称（为空表示不更新）
     * @param avatar   新头像（为空表示不更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateNicknameAndAvatar(Long userId, String nickname, String avatar) {
        LambdaUpdateWrapper<User> updater = new LambdaUpdateWrapper<>();
        updater.eq(User::getId, userId);
        if (StringUtils.hasText(nickname)) {
            updater.set(User::getNickname, nickname);
        }
        if (StringUtils.hasText(avatar)) {
            updater.set(User::getAvatar, avatar);
        }
        userMapper.update(updater);
    }
}
