package com.bjtufood.auth.support;

import com.bjtufood.auth.config.AdminProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 管理端 JWT 签发与校验（TD-18 / TD-19）。
 *
 * <p><b>🔴 与学生端 {@link JwtUtil} 完全隔离</b>：使用<b>独立 secret</b>（{@code admin.jwt.secret}
 * 环境变量 {@code ADMIN_JWT_SECRET}）。共用密钥会使两套凭证在密码学上互通 —— 学生持有自己的
 * 合法 token 即可伪造出管理端身份。两套密钥分离后，任一侧泄露都攻不到另一侧。
 *
 * <p><b>载荷内容</b>：仅 {@code sub}（管理员账号 ID）与 {@code username}，
 * <b>不放口令、不放任何敏感信息</b>。JWT 载荷是明文 Base64（可被任何人解码阅读），
 * 签名只保证「未被篡改」，不提供保密性。
 *
 * <p><b>无状态</b>：服务端不存会话表，凭证自包含全部信息 ⇒ 重启 / 多实例都不影响已签发 token。
 *
 * @see com.bjtufood.auth.config.AdminAuthFilter 每次请求校验
 */
@Component
@Slf4j
public class AdminJwtUtil {

    /** 管理端 JWT 配置（{@code admin.jwt.*}） */
    private final AdminProperties adminProperties;

    /** 缓存的 HMAC 签名密钥（启动时构建一次并复用，避免每请求重建） */
    private volatile SecretKey cachedKey;

    /** HMAC-SHA 算法的密钥长度硬要求：短于此长度 {@code Keys.hmacShaKeyFor} 直接抛异常 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 第二因子票据的有效期（秒）：够完成「打开认证器 → 输入 6 位口令」，短到泄露也几乎无用 */
    private static final long MFA_TICKET_TTL_SECONDS = 300L;

    /** 第二因子票据标记：带此声明的 token <b>不得</b>作为访问凭证（过滤器 fail-closed） */
    private static final String CLAIM_MFA_PENDING = "mfaPending";

    /** 凭证版本声明：与 {@code admin_account.credential_version} 比对，改密即让旧 token 全部失效 */
    private static final String CLAIM_CREDENTIAL_VERSION = "cv";

    public AdminJwtUtil(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    /**
     * 启动期 <b>fail-fast</b>：{@code admin.jwt.secret} 缺失或过短即阻断启动。
     *
     * <p><b>为何不「运行时才失败」</b>：若允许空 secret 启动，则所有登录请求都会失败 ——
     * 用户看到的是「登录总是失败」，排查成本远高于启动直接报错。且弱密钥可被离线爆破，
     * 必须在**启动时**就拦住，而非等到有人尝试伪造。
     */
    @PostConstruct
    public void validateSecretOnStartup() {
        String secret = adminProperties.getJwt().getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "管理端 JWT 密钥未配置：请设置环境变量 ADMIN_JWT_SECRET"
                            + "（≥32 字节随机串，可用 openssl rand -base64 48 生成）");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "管理端 JWT 密钥过短（需 ≥" + MIN_SECRET_BYTES + " 字节）："
                            + "当前 " + secret.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }
        // 预热：构建一次 Key 并做一次签名/验签往返，把密钥构造问题拦在启动期
        String probe = createToken(0L, "probe", 1);
        parseAndValidate(probe);
        log.info("管理端 JWT 密钥校验通过（有效期 {} 秒）", adminProperties.getJwt().getExpirationSeconds());
    }

    /** 获取（懒加载兜底，正常由 {@link #validateSecretOnStartup()} 预热） */
    private SecretKey getKey() {
        SecretKey key = cachedKey;
        if (key == null) {
            synchronized (this) {
                key = cachedKey;
                if (key == null) {
                    key = Keys.hmacShaKeyFor(
                            adminProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
                    cachedKey = key;
                }
            }
        }
        return key;
    }

    /**
     * 签发管理端 token。
     *
     * @param accountId         管理员账号 ID（写入 {@code sub}）
     * @param username          登录名（写入 {@code username}，仅供端上展示）
     * @param credentialVersion 账号当前凭证版本（写入 {@code cv}）—— 改密后与库中现值不再相等，
     *                          既有 token 随即失效
     * @return 紧凑序列化 JWT
     */
    public String createToken(Long accountId, String username, Integer credentialVersion) {
        long ttlMillis = adminProperties.getJwt().getExpirationSeconds() * 1000L;
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("username", username)
                .claim(CLAIM_CREDENTIAL_VERSION, credentialVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(getKey())
                .compact();
    }

    /**
     * 签发第二因子票据（MFA 第一步通过后）。
     *
     * <p>🔴 <b>票据不是 token</b>：它带着 {@code mfaPending} 标记，{@code AdminAuthFilter}
     * 见到该标记一律 401。有效期仅 {@value #MFA_TICKET_TTL_SECONDS} 秒，
     * 作用只有「把第一步的结论带到第二步」。
     *
     * @param accountId 管理员账号 ID
     * @return 紧凑序列化 JWT（含 {@code mfaPending=true}）
     */
    public String createMfaTicket(Long accountId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim(CLAIM_MFA_PENDING, true)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + MFA_TICKET_TTL_SECONDS * 1000L))
                .signWith(getKey())
                .compact();
    }

    /**
     * 该 token 是否为「仅用于第二因子」的票据。
     *
     * @param claims 已校验的载荷
     * @return true = 是票据（不得作为访问凭证）
     */
    public boolean isMfaTicket(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_MFA_PENDING, Boolean.class));
    }

    /**
     * 取 token 内嵌的凭证版本快照。
     *
     * @param claims 已校验的载荷
     * @return 凭证版本；缺失返回 {@code null}（调用方 fail-closed 拒绝）
     */
    public Integer getCredentialVersion(Claims claims) {
        return claims.get(CLAIM_CREDENTIAL_VERSION, Integer.class);
    }

    /**
     * 校验并解析 token —— <b>一次性</b>完成验签 + 过期判定 + 载荷读取。
     *
     * @param token 请求携带的 JWT
     * @return 解析出的 Claims
     * @throws io.jsonwebtoken.JwtException 签名无效 / 已过期 / 格式非法
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从已校验的 Claims 取管理员账号 ID */
    public Long getAccountId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /** token 有效期（秒），供登录响应回显 */
    public long getExpirationSeconds() {
        return adminProperties.getJwt().getExpirationSeconds();
    }
}