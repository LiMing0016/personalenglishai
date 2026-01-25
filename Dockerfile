# ============================================
# 构建阶段：使用 Maven 和 JDK 编译项目
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS build

# 设置工作目录
WORKDIR /build

# 复制 pom.xml 文件（利用 Docker 缓存层优化）
# 如果 pom.xml 没有变化，Maven 依赖下载步骤会被缓存
COPY backend/pom.xml .

# 下载依赖（这一层会被缓存，除非 pom.xml 改变）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY backend/src ./src

# 构建应用（跳过测试以加快构建速度）
# -DskipTests: 跳过测试执行
# clean package: 清理并打包
RUN mvn -DskipTests clean package

# ============================================
# 运行阶段：使用 JRE 运行应用
# ============================================
FROM eclipse-temurin:17-jre

# 设置工作目录
WORKDIR /app

# 安装健康检查工具（wget 用于健康检查）
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

# 创建非 root 用户（安全最佳实践）
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 从构建阶段复制 jar 包
# 使用通配符匹配，适配版本号变化
COPY --from=build /build/target/*.jar app.jar

# 将 /app 目录所有权转移给 appuser
RUN chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# 暴露应用端口
EXPOSE 8080

# 设置 JVM 参数优化
# -XX:+UseContainerSupport: 让 JVM 感知容器环境，正确使用容器分配的内存
# -XX:MaxRAMPercentage=75.0: 限制 JVM 最大堆内存为容器内存的 75%，留出空间给其他进程
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 设置容器入口点
# 使用 exec 形式确保 Java 进程成为 PID 1，能正确接收信号（如 SIGTERM）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

