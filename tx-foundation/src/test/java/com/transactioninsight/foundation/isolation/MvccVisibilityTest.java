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
 * 中文：复刻 MVCC 版本链可见性实验，验证 RR 下 Read View 的可见性规则。
 * English: Reproduce MVCC version-chain visibility, validating RR Read View visibility rules.
 *
 * 预期结果 / Expected Result:
 * 中文：会话A在长事务中始终看到事务开始时的版本；会话B/C提交的更新不可见。
 * English: Session A sees the version at transaction start; updates committed by B/C are invisible.
 *
 * 执行方式 / How to Execute:
 * 中文：运行测试，需本地 MySQL 与 transaction_study。
 * English: Run the test; requires local MySQL and transaction_study.
 */
@SpringBootTest
class MvccVisibilityTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Experiment 4: MVCC visibility under REPEATABLE_READ")
    void mvccVisibility() throws Exception {
        try (Connection init = dataSource.getConnection()) {
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (20000.00, 0, NOW())");
        }

        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection();
             Connection sessionC = dataSource.getConnection()) {
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);
            sessionC.setAutoCommit(false);
            sessionA.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");
            sessionB.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");
            sessionC.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");

            // 中文：会话A首次读取，捕获 Read View（20000）
            // English: Session A first read, capturing Read View (20000)
            BigDecimal a1 = select(sessionA, 1L);
            assertThat(a1).isEqualByComparingTo("20000.00");

            // 中文：会话B更新为 21000 并提交
            // English: Session B updates to 21000 and commits
            update(sessionB, 1L, new BigDecimal("21000.00"));
            sessionB.commit();

            // 中文：会话C再次更新为 22000 并提交
            // English: Session C updates to 22000 and commits
            update(sessionC, 1L, new BigDecimal("22000.00"));
            sessionC.commit();

            // 中文：会话A再次读取，仍看到 20000（不可见后续版本）
            // English: Session A reads again, still sees 20000 (later versions invisible)
            BigDecimal a2 = select(sessionA, 1L);
            assertThat(a2).isEqualByComparingTo("20000.00");

            sessionA.commit();
        }
    }

    private BigDecimal select(Connection conn, long id) throws Exception {
        // 中文：读取余额
        // English: Read balance
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account_transaction WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1);}    
        }
    }

    private void update(Connection conn, long id, BigDecimal amount) throws Exception {
        // 中文：更新余额
        // English: Update balance
        try (PreparedStatement ps = conn.prepareStatement("UPDATE account_transaction SET balance = ?, version = version + 1, updated_at = NOW() WHERE id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}

