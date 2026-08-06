# syntax=docker/dockerfile:1
# Spring Boot 프로덕션 이미지 (멀티스테이지, Maven)
# 워크스페이스 규칙: Spring은 Render 512MB 무료 티어 OOM 위험 → 기본은 로컬 쇼케이스용 이미지.

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src ./src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
# 로컬 쇼케이스 기본값. 실제로 클라우드 배포할 경우 힙 제한 필요(-Xmx360m 등).
CMD ["java", "-jar", "app.jar"]
