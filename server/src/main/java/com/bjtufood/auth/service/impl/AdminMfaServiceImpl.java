package com.bjtufood.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bjtufood.auth.dto.MfaSetupVO;
import com.bjtufood.auth.entity.AdminRecoveryCode;
import com.bjtufood.auth.mapper.AdminAccountMapper;
import com.bjtufood.auth.mapper.AdminRecoveryCodeMapper;
import com.bjtufood.auth.service.AdminMfaService;
import com.bjtufood.auth.support.RecoveryCodes;
import com.bjtufood.auth.support.TotpGenerator;
import com.bjtufood.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMfaServiceImpl implements AdminMfaService {

    /** 认证器中的分组名（Issuer）—— 品牌与管理后台页头一致 */
    private static final String ISSUER = "知行食记管理后台";

    /** 一次下发的恢复码数量：够用（设备丢失后仍有多次机会）又不至于让用户存不完 */
    private static final int RECOVERY_CODE_COUNT = 8;

    private final AdminAccountMapper adminAccountMapper;

    private final AdminRecoveryCodeMapper adminRecoveryCodeMapper;

    @Override
    public MfaSetupVO createSetup(String username) {
        String secret = TotpGenerator.generateSecret();
        MfaSetupVO vo = new MfaSetupVO();
        vo.setSecret(secret);
        vo.setOtpAuthUri(TotpGenerator.buildOtpAuthUri(ISSUER, username, secret));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> confirmSetup(Long accountId, String secret, String code) {
        long step = TotpGenerator.matchedStep(secret, code);
        if (step < 0) {
            // 绑定阶段的失败不按「登录爆破」计数：此刻身份已由账密 + token 双重证明，
            // 口令不符的常见原因是认证器时钟偏差 / 扫错码，不是攻击
            throw new BusinessException("动态口令校验失败，请确认认证器时间准确后重试");
        }
        adminAccountMapper.updateTotpSecret(accountId, secret);
        // 🔴 绑定即记步：确认绑定用的那个口令，不得紧接着再用于登录（否则等于自己制造了一次重放机会）
        adminAccountMapper.advanceTotpStep(accountId, step);
        adminRecoveryCodeMapper.deleteByAdminId(accountId);
        List<String> codes = RecoveryCodes.generate(RECOVERY_CODE_COUNT);
        for (String plain : codes) {
            AdminRecoveryCode row = new AdminRecoveryCode();
            row.setAdminId(accountId);
            row.setCodeHash(RecoveryCodes.hash(plain));
            adminRecoveryCodeMapper.insert(row);
        }
        return codes;
    }

    @Override
    public boolean verifySecondFactor(Long accountId, String totpSecret, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        long step = TotpGenerator.matchedStep(totpSecret, code);
        if (step >= 0) {
            // 条件自增即重放防护：同一时间步的口令只有第一次能用
            return adminAccountMapper.advanceTotpStep(accountId, step) > 0;
        }
        return consumeRecoveryCode(accountId, code);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long accountId) {
        adminAccountMapper.clearTotpSecret(accountId);
        adminRecoveryCodeMapper.deleteByAdminId(accountId);
    }

    /**
     * 消费一枚恢复码（等时比对；用后作废）。
     *
     * @return 是否命中一枚未用过的恢复码
     */
    private boolean consumeRecoveryCode(Long accountId, String code) {
        List<AdminRecoveryCode> rows = adminRecoveryCodeMapper.selectList(
                Wrappers.<AdminRecoveryCode>lambdaQuery()
                        .eq(AdminRecoveryCode::getAdminId, accountId)
                        .isNull(AdminRecoveryCode::getUsedAt));
        for (AdminRecoveryCode row : rows) {
            if (RecoveryCodes.matches(code, row.getCodeHash())) {
                return adminRecoveryCodeMapper.markUsed(row.getId(), LocalDateTime.now()) > 0;
            }
        }
        return false;
    }
}
