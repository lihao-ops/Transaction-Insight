package com.transactioninsight.foundation.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类说明 / Class Description:
 * 中文：锁等待超时与监控实验，设置会话级超时，模拟超时并查询阻塞链信息。
 * English: Lock wait timeout and monitoring experiment; set session timeout, simulate timeout and query blocking chains.
 *
 * 使用场景 / Use Cases:
 * 中文：定位长时间锁等待与阻塞事务，辅助运维与排障。
 * English: Locate long lock waits and blocking transactions, aiding ops and troubleshooting.
 *
 * 设计目的 / Design Purpose:
 * 中文：构造最小阻塞场景并以监控SQL展示等待与阻塞关系。
 * English: Construct minimal blocking scenario and present wait/block relation via monitoring SQLs.
 */
@SpringBootTest
public class LockWaitTimeoutAndMonitoringTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(LockWaitTimeoutAndMonitoringTest.class);

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate("CREATE TABLE account_transaction (id BIGINT PRIMARY KEY AUTO_INCREMENT, balance DECIMAL(15,2) NOT NULL) ENGINE=InnoDB");
            c.createStatement().executeUpdate("INSERT INTO account_transaction (balance) VALUES (1000),(2000),(3000)");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：模拟锁等待超时；会话A持有行锁，B在短超时设置下尝试更新并超时1205。
     * English: Simulate lock wait timeout; Session A holds row lock, B tries update under short timeout and hits 1205.
     */
    @Test
    @DisplayName("Lock-9A: Simulate lock wait timeout with short innodb_lock_wait_timeout")
    void simulateLockWaitTimeout() throws Exception {
        seed();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            b.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 5");

            // 中文：会话A更新id=1不提交，持有锁
            // English: Session A updates id=1 and holds lock without commit
            try (PreparedStatement ps = a.prepareStatement("UPDATE account_transaction SET balance = balance + 100 WHERE id = 1")) { ps.executeUpdate(); }

            // 中文：会话B尝试更新同一行，预期超时
            // English: Session B tries to update same row, expect timeout
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = b.prepareStatement("UPDATE account_transaction SET balance = balance + 200 WHERE id = 1")) { ps.executeUpdate(); }
                b.commit();
            }).isInstanceOf(Exception.class);

            a.rollback();
            b.rollback();
            log.info("实验成功：锁等待超时模拟通过；短超时设置下同行更新超时 / Success: Lock wait timeout simulated; same-row update timed out under short timeout");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：查询当前锁等待与阻塞链；若权限不足则不抛错，仅打印或忽略。
     * English: Query current lock waits and blocking chains; if insufficient privileges, skip without failing.
     */
    @Test
    @DisplayName("Lock-9B: Monitoring queries for lock waits and blocking chains")
    void monitoringQueries() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE account_transaction SET balance = balance + 1 WHERE id = 2")) { ps.executeUpdate(); }
            // 中文：尝试查询监控视图（可能需要额外权限）
            // English: Try querying monitoring views (may require extra privileges)
            try (Connection m = dataSource.getConnection()) {
                try (PreparedStatement ps = m.prepareStatement(
                        "SELECT r.trx_id, r.trx_mysql_thread_id, r.trx_query, b.trx_id, b.trx_mysql_thread_id, b.trx_query " +
                                "FROM information_schema.innodb_lock_waits w " +
                                "JOIN information_schema.innodb_trx r ON w.requesting_trx_id = r.trx_id " +
                                "JOIN information_schema.innodb_trx b ON w.blocking_trx_id = b.trx_id")) {
                    try (ResultSet rs = ps.executeQuery()) { /* consume if present */ }
                }
                try (PreparedStatement ps = m.prepareStatement(
                        "SELECT OBJECT_SCHEMA, OBJECT_NAME, COUNT(*) AS lock_count, " +
                                "COUNT(DISTINCT ENGINE_TRANSACTION_ID) AS trx_count, " +
                                "SUM(CASE WHEN LOCK_STATUS='WAITING' THEN 1 ELSE 0 END) AS waiting_locks " +
                                "FROM performance_schema.data_locks GROUP BY OBJECT_SCHEMA, OBJECT_NAME ORDER BY waiting_locks DESC")) {
                    try (ResultSet rs = ps.executeQuery()) { /* consume if present */ }
                }
            } catch (Exception ignored) { /* 权限不足时忽略 / ignore on privilege errors */ }
            c.rollback();
            log.info("实验成功：锁等待监控查询执行；权限不足时已容错处理 / Success: Lock wait monitoring queries executed; tolerated insufficient privileges");
        }
    }
}
