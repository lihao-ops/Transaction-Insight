package com.transactioninsight.foundation.isolation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目的 / Test Purpose:
 * 中文：复刻 READ COMMITTED 下的不可重复读实验，验证每次 SELECT 都生成新快照。
 * English: Reproduce non-repeatable read under READ COMMITTED, validating a new snapshot per SELECT.
 *
 * 预期结果 / Expected Result:
 * 中文：会话A两次读取中间穿插会话B的提交，第二次读取看到新值。
 * English: Session A's second read sees new value after Session B's commit.
 *
 * 执行方式 / How to Execute:
 * 中文：运行测试，需本地 MySQL 与 transaction_study 数据库。
 * English: Run the test; requires local MySQL and transaction_study.
 */
@SpringBootTest
class ReadCommittedNonRepeatableReadTest {

    @Autowired
    private DataSource dataSource;

    /**
     * 方法说明 / Method Description:
     * 中文：在 RC 隔离下演示不可重复读。
     * English: Demonstrate non-repeatable read under RC isolation.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行异常使测试失败
     */
    @Test
    @DisplayName("Experiment 2: Non-Repeatable Read under READ_COMMITTED")
    void nonRepeatableRead() throws Exception {
        // 中文：准备表与初始数据
        // English: Prepare table and seed data
        try (Connection init = dataSource.getConnection()) {
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (5000.00, 0, NOW())");
        }

        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection()) {
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);
            sessionA.createStatement().execute("SET SESSION transaction_isolation = 'READ-COMMITTED'");
            sessionB.createStatement().execute("SET SESSION transaction_isolation = 'READ-COMMITTED'");

            // 中文：会话A首次读取（5000）
            // English: Session A first read (5000)
            BigDecimal first = selectBalance(sessionA, 1L);
            assertThat(first).isEqualByComparingTo("5000.00");

            // 中文：会话B更新并提交为 6000
            // English: Session B updates to 6000 and commits
            updateBalance(sessionB, 1L, new BigDecimal("6000.00"));
            sessionB.commit();

            // 中文：会话A再次读取，看到新值（6000），不可重复读成立
            // English: Session A reads again, sees 6000; non-repeatable read observed
            BigDecimal second = selectBalance(sessionA, 1L);
            assertThat(second).isEqualByComparingTo("6000.00");

            sessionA.commit();
        }
    }

    private BigDecimal selectBalance(Connection conn, long id) throws Exception {
        // 中文：查询余额
        // English: Query balance
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account_transaction WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
            throw new IllegalStateException("Row not found: id=" + id);
        }
    }

    private void updateBalance(Connection conn, long id, BigDecimal amount) throws Exception {
        // 中文：更新余额
        // English: Update balance
        try (PreparedStatement ps = conn.prepareStatement("UPDATE account_transaction SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}

