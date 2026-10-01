# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Maven 镜像源：云构建直连 Maven Central 会被限流（HTTP 429），改走腾讯云内网镜像
COPY settings.xml /root/.m2/settings.xml
# 先拷 pom 拉依赖，利用缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== 运行阶段 =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/bjtu-food-1.0.0.jar app.jar
# 堆上限按 0.5G 实例保守设置（512m 会因 JVM 额外开销被 OOMKilled）；实例规格 ≥1G 可调回 512m。
ENV JAVA_OPTS="-Xms192m -Xmx384m"
EXPOSE 8080
# ---- 部署红线：**不得开启云托管的「开放接口服务」**（2026-10-01 排障结论，勿删）----
# 开启后平台以旁挂（sidecar）接管容器内对 api.weixin.qq.com 的**全部**请求（实测 DNS 解析到 169.254.10.1），
# 并以**自签证书**终止 TLS；而 JVM 只读自己的信任库（$JAVA_HOME/lib/security/cacerts，不含该自签根）
# ⇒ 所有微信调用失败：登录 500（SSLHandshakeException: PKIX path building failed）、内容安检、云存储取链接。
# 官方 FAQ 对本项目形态（自行维护 access_token + jscode2session + sns 类接口）的口径正是
# 「不需要开启开放接口服务，可以关闭『开放接口服务』然后重新部署一下自己的业务服务」；
# 关闭后出网恢复标准 HTTPS（微信真实证书由 DigiCert 签发，JVM 自带根即可验证）。
# 非微信域名（*.myqcloud.com / COS 等）一直是正常出网，不受影响。
# ⚠️ 该开关状态在**版本创建时锁定**：变更开关后必须**重建版本**才生效（只改开关不重建＝老实例照旧走旁挂）。
# ⚠️ 排障提示：若登录再次报 500「微信登录服务暂不可用」，第一件事就是查该开关是否被重新打开。
# exec：让 java 成为 PID 1，使 SIGTERM 直达 JVM（滚动升级时优雅停机，而非被杀后强退）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
