package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.mapper.AdminAccountMapper;
import com.bjtufood.auth.service.AdminAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AdminAccountMapper adminAccountMapper;

    @Override
    public AdminAccount findByUsername(String username) {
        // 🔴 不抛「账号不存在」异常：登录失败的三种原因（账号不存在 / 密码错 / 已停用）
        // 必须在**同一分支**归并为统一提示，否则可被用于用户名枚举（见 AdminAuthController#login）。
        return adminAccountMapper.selectOne(
                Wrappers.<AdminAccount>lambdaQuery().eq(AdminAccount::getUsername, username));
    }

    @Override
    public AdminAccount findById(Long id) {
        return adminAccountMapper.selectById(id);
    }

    @Override
    public void touchLastLogin(Long accountId) {
        adminAccountMapper.touchLastLogin(accountId, LocalDateTime.now());
    }

    @Override
    public void updatePassword(Long accountId, String passwordHash) {
        adminAccountMapper.updatePassword(accountId, passwordHash, LocalDateTime.now());
    }

    @Override
    public Map<Long, String> mapUsernameByIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> map = new HashMap<>();
        // 只取 id + username 两列：处置人展示位只需要登录名（不取口令哈希等敏感列）
        adminAccountMapper.selectList(Wrappers.<AdminAccount>lambdaQuery()
                        .select(AdminAccount::getId, AdminAccount::getUsername)
                        .in(AdminAccount::getId, accountIds))
                .forEach(a -> map.put(a.getId(), a.getUsername()));
        return map;
    }
}
