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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目的 / Test Purpose:
 * 中文：复刻乐观锁（CAS）转账实验，通过版本号控制并发更新并提供重试框架。
 * English: Reproduce optimistic lock (CAS) transfer using version for concurrent update control with retry.
 *
 * 预期结果 / Expected Result:
 * 中文：先更新成功，后续同版本更新失败，影响行数为 0；重试可成功。
 * English: First update succeeds, subsequent update with stale version fails (rows=0); retry may succeed.
 *
 * 执行方式 / How to Execute:
 * 中文：运行测试，需本地 MySQL 与 transaction_study。
 * English: Run the test; requires local MySQL and transaction_study.
 */
@SpringBootTest
class OptimisticLockCasTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockCasTest.class);

    @Test
    @DisplayName("Experiment 5: Optimistic Lock CAS update with version")
    void optimisticLockCas() throws Exception {
        try (Connection init = dataSource.getConnection()) {
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (500.00, 0, NOW())");
        }

        try (Connection connA = dataSource.getConnection();
             Connection connB = dataSource.getConnection()) {
            connA.setAutoCommit(false);
            connB.setAutoCommit(false);

            // 中文：读取初始版本与余额
            // English: Read initial version and balance
            VersionBalance vbA = readVersionBalance(connA, 1L);
            VersionBalance vbB = readVersionBalance(connB, 1L);
            assertThat(vbA.version).isEqualTo(0);
            assertThat(vbB.version).isEqualTo(0);

            // 中文：会话A扣减 100，版本+1（期望成功）
            // English: Session A deducts 100, version+1 (expect success)
            int affectedA = casUpdate(connA, 1L, vbA.version, vbA.balance.subtract(new BigDecimal("100.00")));
            connA.commit();
            assertThat(affectedA).isEqualTo(1);

            // 中文：会话B基于旧版本尝试扣减 200（期望失败，影响行数为 0）
            // English: Session B tries deduct 200 with stale version (expect fail, rows=0)
            int affectedB = casUpdate(connB, 1L, vbB.version, vbB.balance.subtract(new BigDecimal("200.00")));
            connB.commit();
            assertThat(affectedB).isEqualTo(0);

            // 中文：读取最终余额与版本（应为 400, 1）
            // English: Read final balance and version (should be 400, 1)
            VersionBalance finalVB;
            try (Connection check = dataSource.getConnection()) {
                finalVB = readVersionBalance(check, 1L);
            }
            assertThat(finalVB.balance).isEqualByComparingTo("400.00");
            assertThat(finalVB.version).isEqualTo(1);
            log.info("实验成功：乐观锁（CAS）并发控制验证通过；先提交提升版本，旧版本更新影响行数为0 / Success: Optimistic CAS confirmed; first commit bumps version, stale version update rows=0");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：读取指定记录的版本与余额。
     * English: Read version and balance of specified row.
     *
     * 参数 / Parameters:
     * @param conn 中文：连接 / English: Connection
     * @param id   中文：主键ID / English: Primary key ID
     *
     * 返回值 / Return:
     * 中文：版本与余额 / English: Version and balance
     *
     * 异常 / Exceptions:
     * 中文/英文：查询失败抛出异常
     */
    private VersionBalance readVersionBalance(Connection conn, long id) throws Exception {
        // 中文：查询版本与余额
        // English: Query version and balance
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance, version FROM account_transaction WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new VersionBalance(rs.getBigDecimal(1), rs.getInt(2));
                }
            }
            throw new IllegalStateException("Row not found: id=" + id);
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：基于版本的 CAS 更新（WHERE version=?）。
     * English: Version-based CAS update (WHERE version=?).
     *
     * 参数 / Parameters:
     * @param conn    中文：连接 / English: Connection
     * @param id      中文：主键ID / English: Primary key ID
     * @param version 中文：期望版本 / English: Expected version
     * @param balance 中文：新余额 / English: New balance
     *
     * 返回值 / Return:
     * 中文：影响行数（1 成功，0 冲突） / English: Rows affected (1 success, 0 conflict)
     *
     * 异常 / Exceptions:
     * 中文/英文：更新失败抛出异常
     */
    private int casUpdate(Connection conn, long id, int version, BigDecimal balance) throws Exception {
        // 中文：执行带版本条件的更新
        // English: Execute update with version predicate
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account_transaction SET balance = ?, version = version + 1, updated_at = NOW() WHERE id = ? AND version = ?")) {
            ps.setBigDecimal(1, balance);
            ps.setLong(2, id);
            ps.setInt(3, version);
            return ps.executeUpdate();
        }
    }

    /**
     * 类说明 / Class Description:
     * 中文：值对象，封装版本与余额。
     * English: Value object encapsulating version and balance.
     */
    private record VersionBalance(BigDecimal balance, int version) {}
}
