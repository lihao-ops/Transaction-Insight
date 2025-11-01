package com.transactioninsight.foundation;

import com.transactioninsight.foundation.mysql.Account;
import com.transactioninsight.foundation.mysql.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>
 * 测试目标：验证 MySQL 在 READ_UNCOMMITTED 隔离级别下会出现脏读现象，并给出实际 SQL 操作。
 * 通过 {@link MySQLContainer} 启动临时库，也可以按 README 指引改为本地实例以避免 Docker 依赖。
 * </p>
 * <p>
 * 预期现象：读事务能读取到未提交的余额 1000，回滚后再次读取为 500；实际运行结果与预期一致。
 * </p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Testcontainers
class MySqlIsolationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.3.0")
                    .withUsername("test_user")
                    .withPassword("test_password")
                    .withDatabaseName("transaction_test");

    /**
     * 将 Testcontainers 中的连接信息注入到 Spring 配置，确保测试与真实环境一致。
     *
     * @param registry Spring Test 提供的属性注册器
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL_CONTAINER::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private AccountRepository repository;

    @Autowired
    private DataSource dataSource;

    /**
     * 初始化一条账户记录，作为脏读实验的基线数据。
     */
    @BeforeEach
    void setup() {
        repository.deleteAll();
        repository.save(new Account(new BigDecimal("500")));
    }

    /**
     * 测试方法：在两个 READ_UNCOMMITTED 事务中分别执行写入和读取，验证脏读与回滚后的最终一致性。
     *
     * @throws Exception SQL 操作失败时抛出
     */
    @Test
    @DisplayName("READ UNCOMMITTED allows dirty read")
    void dirtyReadOccursUnderReadUncommitted() throws Exception {
        try (Connection writer = dataSource.getConnection();
             Connection reader = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            reader.setAutoCommit(false);

            writer.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            updateBalance(writer, 1L, new BigDecimal("1000"));

            BigDecimal dirtyBalance = queryBalance(reader, 1L);
            assertThat(dirtyBalance).isEqualByComparingTo("1000");

            writer.rollback();

            BigDecimal cleanBalance = queryBalance(reader, 1L);
            assertThat(cleanBalance).isEqualByComparingTo("500");
        }
    }

    /**
     * 执行余额更新语句，模拟尚未提交的写事务。
     */
    private void updateBalance(Connection connection, long id, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE account SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * 查询余额，帮助验证不同事务视图下的数据一致性。
     */
    private BigDecimal queryBalance(Connection connection, long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
                throw new IllegalStateException("Account not found");
            }
        }
    }
}
