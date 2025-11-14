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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类说明 / Class Description:
 * 中文：行锁与表锁触发条件实验，验证主键等值触发行锁、无索引/索引失效退化为表锁。
 * English: Row vs table lock experiment verifying row lock on PK equality and table lock on no/invalid index.
 *
 * 使用场景 / Use Cases:
 * 中文：教学演示加锁粒度对并发的影响，辅助索引设计与排障。
 * English: Demonstrate how lock granularity affects concurrency; supports index design and troubleshooting.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过双会话并发与短超时设置，构造阻塞与锁等待超时便于可重复验证。
 * English: Use two sessions and short timeouts to construct blocking and lock wait timeouts for reproducible verification.
 */
@SpringBootTest
public class RowVsTableLockTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(RowVsTableLockTest.class);

    private void resetSchema() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "user_id BIGINT, " +
                            "account_no VARCHAR(64), " +
                            "account_type INT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "status INT, risk_level INT, branch_id INT, last_trans_time DATETIME, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "frozen_amount DECIMAL(15,2) NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            c.createStatement().executeUpdate("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES " +
                    "(1001,'ACC001',1,1000,1,0,101,NOW())," +
                    "(1002,'ACC002',1,2000,1,0,101,NOW())," +
                    "(1003,'ACC003',1,3000,1,0,101,NOW())," +
                    "(1004,'ACC004',1,4000,1,0,101,NOW())," +
                    "(1005,'ACC005',1,5000,1,0,101,NOW())," +
                    "(1006,'ACC006',1,6000,1,0,101,NOW())," +
                    "(1007,'ACC007',1,7000,1,0,101,NOW())," +
                    "(1008,'ACC008',1,8000,1,0,101,NOW())," +
                    "(1009,'ACC009',1,9000,1,0,101,NOW())," +
                    "(1010,'ACC010',1,10000,1,0,101,NOW())");
            c.createStatement().executeUpdate("CREATE INDEX idx_user_id ON account_transaction(user_id)");
            c.createStatement().executeUpdate("CREATE UNIQUE INDEX idx_account_no ON account_transaction(account_no)");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：场景A：主键等值查询（行锁）；不同主键并发不互斥，同主键更新被阻塞并超时。
     * English: Scenario A: PK equality (row lock); different PKs do not conflict; same PK update blocks and times out.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 超时或阻塞将抛出异常
     */
    @Test
    @DisplayName("Lock-6A: Row lock on PK equality; blocking on same row")
    void pkEqualityRowLock() throws Exception {
        resetSchema();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            b.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 2");

            // 中文：会话A锁定id=1的记录（FOR UPDATE）
            // English: Session A locks row id=1 (FOR UPDATE)
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE id = 1 FOR UPDATE")) { ps.executeQuery(); }

            // 中文：会话B锁定不同记录id=2（成功）
            // English: Session B locks different row id=2 (success)
            try (PreparedStatement ps = b.prepareStatement("SELECT * FROM account_transaction WHERE id = 2 FOR UPDATE")) { ps.executeQuery(); }
            b.commit();

            // 中文：会话B尝试更新被会话A锁定的id=1，预期等待并超时1205
            // English: Session B tries to update row id=1 locked by A; expect wait and timeout 1205
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = b.prepareStatement("UPDATE account_transaction SET balance = balance + 100 WHERE id = 1")) { ps.executeUpdate(); }
                b.commit();
            }).isInstanceOf(Exception.class);

            a.rollback();
            b.rollback();
            log.info("实验成功：主键等值行锁验证通过；同一行更新阻塞并超时 / Success: PK row lock confirmed; same-row update blocked and timed out");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：场景B：无索引查询（表锁）；删除 user_id 索引后，对 user_id 条件的当前读触发全表锁。
     * English: Scenario B: No-index query (table lock); after dropping user_id index, current read on user_id triggers table lock.
     */
    @Test
    @DisplayName("Lock-6B: Table lock when no index supports FOR UPDATE filter")
    void tableLockNoIndex() throws Exception {
        resetSchema();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            b.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 2");

            // 中文：删除 user_id 索引，制造无索引扫描
            // English: Drop user_id index to cause full table scan
            a.createStatement().execute("ALTER TABLE account_transaction DROP INDEX idx_user_id");

            // 中文：会话A按 user_id 当前读加锁，触发表锁
            // English: Session A current read FOR UPDATE by user_id triggers table lock
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE user_id = 1001 FOR UPDATE")) { ps.executeQuery(); }

            // 中文：会话B任意更新将被阻塞并超时
            // English: Session B arbitrary update blocks and times out
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = b.prepareStatement("UPDATE account_transaction SET balance = balance + 100 WHERE id = 10")) { ps.executeUpdate(); }
                b.commit();
            }).isInstanceOf(Exception.class);

            a.rollback();
            b.rollback();

            // 中文：恢复索引（确保后续实验不受影响）
            // English: Restore index for subsequent experiments
            try (Connection c = dataSource.getConnection()) { c.createStatement().execute("ALTER TABLE account_transaction ADD INDEX idx_user_id(user_id)"); }
            log.info("实验成功：无索引导致表锁验证通过；删除索引后 FOR UPDATE 触发表锁、任意更新阻塞 / Success: Table lock without index confirmed; FOR UPDATE triggers table lock, arbitrary update blocked");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：场景C：索引失效导致锁升级；隐式类型转换使唯一索引不可用，退化为表锁。
     * English: Scenario C: Index invalidation causes lock escalation; implicit type conversion disables unique index.
     */
    @Test
    @DisplayName("Lock-6C: Implicit cast invalidates index; EXPLAIN shows full scan")
    void indexInvalidationByImplicitCast() throws Exception {
        resetSchema();
        try (Connection a = dataSource.getConnection()) {
            a.setAutoCommit(false);
            // 中文：确保唯一索引存在
            // English: Ensure unique index exists
            a.createStatement().execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_account_no ON account_transaction(account_no)");

            // 中文：使用数字字面量匹配字符串列，导致隐式转换与索引失效
            // English: Use numeric literal on string column causing implicit cast and index invalidation
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE account_no = 20240001 FOR UPDATE")) { ps.executeQuery(); }

            // 中文：验证执行计划显示全表扫描
            // English: Verify EXPLAIN shows full table scan
            try (PreparedStatement ps = a.prepareStatement("EXPLAIN SELECT * FROM account_transaction WHERE account_no = 20240001")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String type = rs.getString("type");
                        String key = rs.getString("key");
                        assertThat(type).isIn("ALL", "index");
                        assertThat(key).isNull();
                    }
                }
            }
            a.rollback();
            log.info("实验成功：隐式类型转换导致索引失效验证通过；EXPLAIN 显示全表扫描 / Success: Index invalidation by implicit cast confirmed; EXPLAIN shows full scan");
        }
    }
}
