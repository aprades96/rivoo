# =============================================================================
# Dockerfile para admin-service
# Build desde la raiz del monorepo: docker build -f admin-service/Dockerfile .
# =============================================================================

FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY rivoo-common/pom.xml rivoo-common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY salon-service/pom.xml salon-service/pom.xml
COPY staff-service/pom.xml staff-service/pom.xml
COPY client-service/pom.xml client-service/pom.xml
COPY appointment-service/pom.xml appointment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY billing-service/pom.xml billing-service/pom.xml
COPY admin-service/pom.xml admin-service/pom.xml

RUN apt-get update && apt-get install -y maven && \
    mvn dependency:go-offline -B -q || true

COPY . .
RUN mvn clean package -DskipTests -pl rivoo-common,admin-service -am -B -q

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /build/admin-service/target/*.jar app.jar
EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8088/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseZGC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
