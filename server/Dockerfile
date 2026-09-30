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
# exec：让 java 成为 PID 1，使 SIGTERM 直达 JVM（滚动升级时优雅停机，而非被杀后强退）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
