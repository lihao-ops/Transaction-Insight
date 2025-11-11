package com.transactioninsight.springcore.mysql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
public abstract class AbstractMySqlTransactionIntegrationTest {

    private static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("tx_lab")
            .withUsername("tx_user")
            .withPassword("tx_pass");

    @BeforeAll
    static void startContainer() {
        MYSQL_CONTAINER.start();
    }

    @AfterAll
    static void stopContainer() {
        MYSQL_CONTAINER.stop();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
    }
}
