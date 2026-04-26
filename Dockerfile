# =============================================================
# Multi-stage build
# Stage 1: Build with Maven + JDK 21
# Stage 2: Run with slim JRE 21
# =============================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先複製 pom.xml 以利用 layer cache
COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline

# 複製原始碼後打包
COPY src ./src
RUN mvn -B -q -DskipTests package

# 解壓 jar 內容（為了之後 layered 啟動更快）
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
