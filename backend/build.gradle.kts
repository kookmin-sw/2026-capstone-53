plugins {
	java
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.todayway"
version = "0.0.1-SNAPSHOT"

// Docker Engine 29.x ↔ docker-java SDK 호환을 위해 testcontainers 버전 핀
// (Spring Boot BOM이 잡는 1.21.2가 Docker 29와 HTTP 400 빈응답 이슈 → 1.21.4로 업그레이드)
extra["testcontainers.version"] = "1.21.4"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.projectlombok:lombok")

	// DB 마이그레이션 (Flyway)
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")

	// 인증 (Spring Security + JJWT 0.12.x)
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// Web Push (VAPID) — Step 7 이상진 사용 예정, 의존성만 미리
	implementation("nl.martijndwars:web-push:5.1.1")
	implementation("org.bouncycastle:bcprov-jdk18on:1.78")

	// ULID 생성기 (외부 노출 ID용 26자 Crockford Base32)
	implementation("com.github.f4b6a3:ulid-creator:5.2.3")

	// 외부 API 호출용 HttpClient (커넥션 풀 + 동시성 효율) — SimpleClientHttpRequestFactory 대체
	implementation("org.apache.httpcomponents.client5:httpclient5")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")

	// 통합 테스트 (Testcontainers MySQL)
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:mysql")
	testImplementation("org.springframework.security:spring-security-test")
}

// 운영 이미지 빌드 시 plain JAR 불필요 — bootJar만 산출 (Dockerfile wildcard 충돌 방지)
tasks.named<Jar>("jar") {
	enabled = false
}

tasks.withType<Test> {
	useJUnitPlatform()
}
