plugins {
	java
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.fcstreamtube"
version = "0.0.1-SNAPSHOT"
description = "FC StreamTube Backend API"

// Default: Java 25 (local). Docker builds pass -PjavaVersion=21 to avoid
// the Kotlin Gradle DSL bug that fails to parse version strings like "25.0.x".
val javaVersion = (project.findProperty("javaVersion") as? String)?.toInt() ?: 25

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(javaVersion)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// OrbStack requires Docker API >= 1.40; docker-java shaded in Testcontainers defaults to 1.32.
	// "api.version" is the property key read by the shaded DefaultDockerClientConfig.Builder.
	jvmArgs("-Dapi.version=1.41")
}
