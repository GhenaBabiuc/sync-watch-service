FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

USER appuser

ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 3000 3001

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
