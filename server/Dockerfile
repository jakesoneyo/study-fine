# syntax=docker/dockerfile:1
# Spring Boot 프로덕션 이미지 (멀티스테이지, Maven)
# Render 512MB 무료 티어 시도용 — 힙+메타스페이스를 명시적으로 제한해서
# JVM이 컨테이너 메모리 한도를 넘기 전에 스스로 억제하게 한다.
# (제한 안 하면 OOM killer가 프로세스를 그냥 죽여버려서 "떴다가 죽는" 상태가 됨)

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
# 힙 256MB + 메타스페이스 180MB로 제한(springdoc·Hibernate·Security가 클래스를 많이 로드해서
# 96MB로는 기동 중 "OutOfMemoryError: Metaspace"로 죽는 걸 실측함). SerialGC로 GC 자체의
# 메모리 오버헤드도 줄인다. 나머지 ~76MB를 스레드/네이티브 여유로 남김.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=180m -XX:+UseSerialGC"
CMD ["java", "-jar", "app.jar"]
