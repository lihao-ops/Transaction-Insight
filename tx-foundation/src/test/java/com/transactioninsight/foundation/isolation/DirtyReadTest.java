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
 * 中文：基于 MySQL 的 READ UNCOMMITTED 隔离级别，完整复刻脏读实验，验证未提交数据可见。
 * English: Full reproduction of dirty read under MySQL READ UNCOMMITTED, verifying visibility of uncommitted data.
 *
 * 预期结果 / Expected Result:
 * 中文：会话A在会话B未提交更新后，能读取到新的余额；会话B回滚后，会话A再次读取恢复原值。
 * English: Session A reads new balance after Session B's uncommitted update; after B's rollback, A reads original value.
 *
 * 执行方式 / How to Execute:
 * 中文：直接运行测试，需本地 MySQL 数据库与 transaction_study 库。
 * English: Run the test directly; requires local MySQL and transaction_study database.
 */
@SpringBootTest
class DirtyReadTest {

    @Autowired
    private DataSource dataSource;

    /**
     * 方法说明 / Method Description:
     * 中文：构建两会话，在 READ_UNCOMMITTED 下演示脏读与回滚效果。
     * English: Build two sessions under READ_UNCOMMITTED to demonstrate dirty read and rollback effect.
     *
     * 参数 / Parameters:
     * 无
     *
     * 返回值 / Return:
     * 中文：无 / English: None
     *
     * 异常 / Exceptions:
     * 中文/英文：数据库不可用或 SQL 执行失败将使测试失败
     */
    @Test
    @DisplayName("Experiment 1: Dirty Read under READ_UNCOMMITTED")
    void dirtyRead() throws Exception {
        // 中文：准备表结构与初始数据（如果不存在则创建）
        // English: Prepare table and seed data (create if not exists)
        try (Connection init = dataSource.getConnection()) {
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (15000.00, 0, NOW())");
        }

        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection()) {
            // 中文：关闭自动提交以手动控制事务
            // English: Disable auto-commit for manual transaction control
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);

            // 中文：设置会话隔离级别为 READ UNCOMMITTED
            // English: Set session isolation level to READ UNCOMMITTED
            sessionA.createStatement().execute("SET SESSION transaction_isolation = 'READ-UNCOMMITTED'");
            sessionB.createStatement().execute("SET SESSION transaction_isolation = 'READ-UNCOMMITTED'");

            // 中文：会话A读取初始余额（15000）
            // English: Session A reads initial balance (15000)
            BigDecimal initBalance = selectBalance(sessionA, 1L);
            assertThat(initBalance).isEqualByComparingTo("15000.00");

            // 中文：会话B进行未提交的扣款（-5000）
            // English: Session B performs uncommitted deduction (-5000)
            updateBalance(sessionB, 1L, new BigDecimal("10000.00"));

            // 中文：会话A再次读取，发生脏读（10000）
            // English: Session A reads again, dirty read occurs (10000)
            BigDecimal dirty = selectBalance(sessionA, 1L);
            assertThat(dirty).isEqualByComparingTo("10000.00");

            // 中文：会话B回滚
            // English: Session B rolls back
            sessionB.rollback();

            // 中文：会话A再次读取，恢复为原值（15000）
            // English: Session A reads again, restored to original value (15000)
            BigDecimal restored = selectBalance(sessionA, 1L);
            assertThat(restored).isEqualByComparingTo("15000.00");

            // 中文：提交会话A
            // English: Commit session A
            sessionA.commit();
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：查询指定记录的余额。
     * English: Query balance of the specified record.
     *
     * 参数 / Parameters:
     * @param conn 中文：连接 / English: Connection
     * @param id   中文：主键ID / English: Primary key ID
     *
     * 返回值 / Return:
     * 中文：余额数值 / English: Balance value
     *
     * 异常 / Exceptions:
     * 中文/英文：SQL 执行失败抛出异常
     */
    private BigDecimal selectBalance(Connection conn, long id) throws Exception {
        // 中文：执行查询以获取余额
        // English: Execute query to fetch balance
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account_transaction WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
            throw new IllegalStateException("Row not found: id=" + id);
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：更新指定记录的余额（不提交）。
     * English: Update balance of the specified record (uncommitted).
     *
     * 参数 / Parameters:
     * @param conn   中文：连接 / English: Connection
     * @param id     中文：主键ID / English: Primary key ID
     * @param amount 中文：新余额 / English: New balance
     *
     * 返回值 / Return:
     * 中文：无 / English: None
     *
     * 异常 / Exceptions:
     * 中文/英文：SQL 执行失败抛出异常
     */
    private void updateBalance(Connection conn, long id, BigDecimal amount) throws Exception {
        // 中文：执行更新以写入新余额
        // English: Execute update to write new balance
        try (PreparedStatement ps = conn.prepareStatement("UPDATE account_transaction SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}

