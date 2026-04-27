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
		registry.add("jwt.secret", () -> "test-secret-base64-padded-32bytes-long==");
	}

	@Test
	void contextLoads() {
	}

}
