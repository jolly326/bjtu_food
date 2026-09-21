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
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
