package com.todayway.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BackendApplicationTests {

	@Container
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("routine_commute");

	@DynamicPropertySource
	static void mysqlProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
		registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LXBhZGRlZC0zMmJ5dGVzLWxvbmc9PQ==");
		// contextLoads 만 검증하지만 PushScheduler 가 빈 등록 시 contextLoads 자체가 실 ODsay/Push
		// 호출 위험을 부른다 — 본 토글로 빈 자체가 등록되지 않음 (PushSchedulingConfig
		// @ConditionalOnProperty enabled=false → @EnableScheduling X).
		registry.add("push.scheduler.enabled", () -> "false");
	}

	@Test
	void contextLoads() {
	}

}
