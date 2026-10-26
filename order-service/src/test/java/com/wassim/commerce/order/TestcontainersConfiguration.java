package com.wassim.commerce.order;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    org.springframework.test.context.DynamicPropertyRegistrar jwtTestProperties() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String secret = java.util.Base64.getEncoder().encodeToString(key);
        return registry -> registry.add("jwt.secret", () -> secret);
    }

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
