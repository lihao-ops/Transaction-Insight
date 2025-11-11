package com.transactioninsight.foundation;

import com.transactioninsight.foundation.mysql.Account;
import com.transactioninsight.foundation.mysql.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

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
 * 使用 H2 以 MySQL 模式运行，避免对 Docker 或外部数据库的依赖，同时保留相同的隔离级别行为。
 * </p>
 * <p>
 * 事务知识点：隔离性（Isolation）中的脏读（Dirty Read），通过对比提交前后的余额演示读写冲突。
 * </p>
 * <p>
 * 预期现象：读事务能读取到未提交的余额 1000，回滚后再次读取为 500；实际运行结果与预期一致。
 * </p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tx_foundation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MySqlIsolationIntegrationTest {

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
