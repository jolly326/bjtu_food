package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bjtufood.auth.entity.AdminAccount;
import com.bjtufood.auth.mapper.AdminAccountMapper;
import com.bjtufood.auth.service.AdminAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
}