FROM eclipse-temurin:17-jdk-jammy AS builder

ARG GRADLE_TASK
ARG JAR_DIR

WORKDIR /workspace
COPY . .

RUN chmod +x gradlew \
    && ./gradlew "${GRADLE_TASK}" --no-daemon -x test \
    && JAR_FILE="$(find "${JAR_DIR}" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /app.jar

FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 10001 --create-home spring

WORKDIR /app
COPY --from=builder --chown=spring:spring /app.jar app.jar

USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
