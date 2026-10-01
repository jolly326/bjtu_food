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
# ---- 出网证书同步（2026-10-01 排障修复：code2Session 报 PKIX path building failed）----
# 现象：容器内调用 https://api.weixin.qq.com 抛 SSLHandshakeException。
# 实况（经云托管 Web Shell 实测）：DNS 把 api.weixin.qq.com 解析到 169.254.10.1（链路本地地址 = 出网代理），
# 代理用**自签证书**替换真实证书；该根被平台装进 **OS 证书库**
# （/usr/local/share/ca-certificates/certificate.crt ⇒ curl / openssl 均可验证通过），
# 但 **JVM 只读自己的 $JAVA_HOME/lib/security/cacerts**（JSSE 独立信任库），镜像里也没有
# ca-certificates-java 去同步 ⇒ Java 侧永远不信任该根。
# 处理：容器启动时把 OS 侧新增的根逐张导入 JVM 信任库，再 exec java。
# 为何放在启动期而非构建期：certificate.crt **由平台在容器启动时注入**（不在基础镜像内），
# 构建阶段访问不到；且平台若轮换该根，启动期导入能自动跟上。
# 失败处理：单张导入失败只打一行告警，不阻断启动（诊断期可删本段、改回纯 exec java）。
# exec：让 java 成为 PID 1，使 SIGTERM 直达 JVM（滚动升级时优雅停机，而非被杀后强退）
ENTRYPOINT ["sh", "-c", "for f in /usr/local/share/ca-certificates/*.crt; do [ -f \"$f\" ] || continue; keytool -importcert -noprompt -alias \"osca-${f##*/}\" -file \"$f\" -keystore \"$JAVA_HOME/lib/security/cacerts\" -storepass changeit >/dev/null 2>&1 || echo \"[cert-sync] X509 import failed: $f\"; done; exec java $JAVA_OPTS -jar app.jar"]
