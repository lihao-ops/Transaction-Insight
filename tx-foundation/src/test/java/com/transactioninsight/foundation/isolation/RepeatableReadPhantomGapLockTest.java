package com.transactioninsight.foundation.isolation;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 测试目的 / Test Purpose:
 * 中文：复刻 RR 级别下的快照读与当前读行为，并演示间隙锁阻止插入。
 * English: Reproduce snapshot vs current read under RR and demonstrate gap lock preventing inserts.
 *
 * 预期结果 / Expected Result:
 * 中文：快照读不看到新插入；当前读看到最新数据；间隙锁阻止插入并抛超时。
 * English: Snapshot read doesn't see inserts; current read sees latest; gap lock prevents insert with timeout.
 *
 * 执行方式 / How to Execute:
 * 中文：运行测试，需本地 MySQL 与 transaction_study。
 * English: Run the test; requires local MySQL and transaction_study.
 */
@SpringBootTest
class RepeatableReadPhantomGapLockTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(RepeatableReadPhantomGapLockTest.class);

    @Test
    @DisplayName("Experiment 3A: Snapshot vs Current Read under REPEATABLE_READ")
    void snapshotVsCurrentRead() throws Exception {
        try (Connection init = dataSource.getConnection()) {
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (12000.00, 0, NOW()), (8000.00, 0, NOW()), (5000.00, 0, NOW())");
        }

        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection()) {
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);
            sessionA.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");
            sessionB.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");

            // 中文：会话A快照读统计 >10000 的记录数
            // English: Session A snapshot read count of balances >10000
            int count1 = countOver(sessionA, 10000);
            assertThat(count1).isGreaterThanOrEqualTo(1);

            // 中文：会话B插入一条 balance=25000 并提交
            // English: Session B inserts balance=25000 and commits
            try (PreparedStatement ps = sessionB.prepareStatement("INSERT INTO account_transaction (balance, version, updated_at) VALUES (25000.00, 0, NOW())")) {
                ps.executeUpdate();
            }
            sessionB.commit();

            // 中文：会话A快照读不变（仍为旧视图）
            // English: Session A snapshot read unchanged (old view)
            int count2 = countOver(sessionA, 10000);
            assertThat(count2).isEqualTo(count1);

            // 中文：会话A当前读（FOR UPDATE）看到新的总数
            // English: Session A current read (FOR UPDATE) sees new total
            int currentCount = countOverForUpdate(sessionA, 10000);
            assertThat(currentCount).isGreaterThan(count1);

            sessionA.commit();
            log.info("实验成功：RR 快照读与当前读行为验证通过；快照读不变、当前读可见新数据 / Success: RR snapshot vs current read confirmed; snapshot unchanged, current read sees latest");
        }
    }

    @Test
    @DisplayName("Experiment 3B: Gap Lock prevents INSERT in range")
    void gapLockPreventsInsert() throws Exception {
        try (Connection init = dataSource.getConnection()) {
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (500.00, 0, NOW()), (5000.00, 0, NOW()), (6500.00, 0, NOW()), (7200.00, 0, NOW()), (15000.00, 0, NOW())");
        }

        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection()) {
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);
            sessionA.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");
            sessionB.createStatement().execute("SET SESSION transaction_isolation = 'REPEATABLE-READ'");
            // 中文：缩短锁等待时间以加速测试失败路径
            // English: Shorten lock wait timeout to speed up failing path
            sessionB.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 2");

            // 中文：会话A在范围内当前读加锁，触发 Next-Key Lock
            // English: Session A current read with range lock triggers Next-Key Lock
            countRangeForUpdate(sessionA, 8000, 12000);

            // 中文：会话B尝试在间隙内插入（预期超时 1205）
            // English: Session B attempts INSERT into gap (expect timeout 1205)
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = sessionB.prepareStatement("INSERT INTO account_transaction (balance, version, updated_at) VALUES (9000.00, 0, NOW())")) {
                    ps.executeUpdate();
                }
                sessionB.commit();
            }).isInstanceOf(Exception.class);

            sessionA.rollback();
            sessionB.rollback();
            log.info("实验成功：RR 间隙锁阻止插入验证通过；范围内 INSERT 阻塞并超时 / Success: RR gap lock confirmed; in-range INSERT blocked and timed out");
        }
    }

    private int countOver(Connection conn, int threshold) throws Exception {
        // 中文：快照读统计满足条件的记录数
        // English: Snapshot read to count records over threshold
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM account_transaction WHERE balance > ?")) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1);}    
        }
    }

    private int countOverForUpdate(Connection conn, int threshold) throws Exception {
        // 中文：当前读（加锁）统计满足条件的记录数
        // English: Current read (locking) to count records over threshold
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM account_transaction WHERE balance > ? FOR UPDATE")) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1);}    
        }
    }

    private void countRangeForUpdate(Connection conn, int low, int high) throws Exception {
        // 中文：在指定范围内进行当前读以触发 Next-Key Lock
        // English: Perform current read in range to trigger Next-Key Lock
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM account_transaction WHERE balance BETWEEN ? AND ? FOR UPDATE")) {
            ps.setInt(1, low);
            ps.setInt(2, high);
            try (ResultSet ignored = ps.executeQuery()) { /* consume */ }
        }
    }
}
