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
 * 测试目的 / Test Purpose:
 * 中文：验证 READ_UNCOMMITTED 隔离级别下的脏读现象，并通过回滚恢复最终一致性。
 * English: Validate dirty read under READ_UNCOMMITTED and restore eventual consistency via rollback.
 *
 * 预期结果 / Expected Result:
 * 中文：未提交写事务的值可被读事务读取；回滚后读事务再次读取为先前值。
 * English: Value from uncommitted write is readable; after rollback, subsequent read returns original value.
 *
 * 执行方式 / How to Execute:
 * 中文：直接运行单测，使用 H2（MySQL 模式）作为内存数据库，无需外部依赖。
 * English: Run the test; uses H2 (MySQL mode) in-memory DB, no external dependencies required.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tx_foundation;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=root",
        "spring.datasource.password=Q836184425",
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
        /**
         * 方法说明 / Method Description:
         * 中文：验证两个 READ_UNCOMMITTED 事务内的脏读与回滚后读到原值的行为。
         * English: Validate dirty read within two READ_UNCOMMITTED transactions and reading original value after rollback.
         *
         * 测试目标 / What is tested:
         * 中文：同一记录在未提交更新后的可读性与回滚后的一致性。
         * English: Readability of uncommitted update and consistency after rollback for the same record.
         *
         * 输入/输出 / Input/Output:
         * 中文：输入为账户 ID；输出为两次读取的余额（1000 与 500）。
         * English: Input is account ID; output is two balances (1000 and 500).
         *
         * 预期结果 / Expected:
         * 中文：第一次读取到 1000（脏读），回滚后读取到 500（原值）。
         * English: First read 1000 (dirty read), after rollback read 500 (original).
         */
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
