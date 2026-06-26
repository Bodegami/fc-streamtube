# --- Build stage ---
# Uses Java 21 JDK to avoid a Kotlin Gradle DSL bug that fails to parse
# Java 25.x version strings (present in Gradle 8.x / Kotlin Gradle plugin).
# The project is compiled targeting Java 21 bytecode (-PjavaVersion=21).
# Java 25 JRE in the runtime stage is backwards-compatible with Java 21 bytecode.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q -PjavaVersion=21
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test -PjavaVersion=21

# --- Runtime stage ---
FROM eclipse-temurin:25-jre-alpine AS runner
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
