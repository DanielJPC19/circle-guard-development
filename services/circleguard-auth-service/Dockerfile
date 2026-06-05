FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S circleguard && adduser -S circleguard -G circleguard

WORKDIR /app

ARG JAR_FILE
COPY ${JAR_FILE} app.jar
RUN chown circleguard:circleguard app.jar

USER circleguard

EXPOSE 8180

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8180/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
