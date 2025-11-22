package com.lockWait;

import com.transactioninsight.foundation.TransactionFoundationApplication;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@SpringBootTest(classes = TransactionFoundationApplication.class)
@Slf4j
public class LockWaitInspectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Resource
    private DataSource dataSource;

    /**
     * 确认表中存在id=1的数据
     */
    @BeforeEach
    public void initAccountData() {
        jdbcTemplate.update("""
                    INSERT INTO account_lock(id, account_no, balance, version)
                    VALUES (1, 'A1001', 100, 0)
                    ON DUPLICATE KEY UPDATE balance = balance;
                """);
        log.warn("【BeforeEach】已确保 id=1 存在");
    }

    /**
     * 目的：
     * 1）线程 A 获取 row lock（持锁）
     * 2）线程 B 尝试 UPDATE 同一行 → 被阻塞（等待锁）
     * 3）主线程执行锁等待诊断 SQL → 必定查到等待链
     */
    @Test
    public void testLockWaitInspection() throws Exception {
        log.warn("========== 测试开始：制造锁等待 ==========");

        Thread t1 = new Thread(this::holdLock, "T-HOLDER");
        Thread t2 = new Thread(this::waitLock, "T-WAITER");

        t1.start();
        Thread.sleep(300); // 确保持锁成功
        t2.start();

        Thread.sleep(2000); // 等待 B 进入阻塞状态

        log.warn("========== 查询锁等待链 ==========");

        printLockWaitInfo();

        log.warn("========== 测试结束（请手动 commit/rollback 连接） ==========");
    }

    /**
     * A 线程：获取行锁且不提交
     */
    private void holdLock() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            log.warn("[T-HOLDER] 获取锁：UPDATE id=1");
            stmt.execute("UPDATE account_lock SET balance = balance - 1 WHERE id = 1");

            // 不提交、保持锁
            Thread.sleep(10000);

        } catch (Exception e) {
            log.error("[T-HOLDER] 异常", e);
        }
    }

    /**
     * B 线程：尝试更新同一行 → 进入锁等待
     */
    private void waitLock() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            log.warn("[T-WAITER] 尝试获取同一行锁...");
            stmt.execute("UPDATE account_lock SET balance = balance - 1 WHERE id = 1");

            log.error("[T-WAITER] !! 不应该到达这里（应该被阻塞）");

        } catch (Exception e) {
            log.error("[T-WAITER] 异常", e);
        }
    }

    /**
     * MySQL 8.0：查询锁等待链
     */
    private void printLockWaitInfo() throws Exception {

        String sql = "SELECT " +
                "r.trx_id AS waiting_trx_id, " +
                "r.trx_mysql_thread_id AS waiting_thread, " +
                "r.trx_query AS waiting_query, " +
                "b.trx_id AS blocking_trx_id, " +
                "b.trx_mysql_thread_id AS blocking_thread, " +
                "b.trx_query AS blocking_query " +
                "FROM performance_schema.data_lock_waits w " +
                "JOIN information_schema.innodb_trx r " +
                "ON w.REQUESTING_ENGINE_TRANSACTION_ID = r.trx_id " +
                "JOIN information_schema.innodb_trx b " +
                "ON w.BLOCKING_ENGINE_TRANSACTION_ID = b.trx_id";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean found = false;

            while (rs.next()) {
                found = true;
                log.error(
                        "\n=========================\n" +
                                "【锁等待链检测到】\n" +
                                "等待事务 waiting_trx_id     = {}\n" +
                                "等待线程 waiting_thread      = {}\n" +
                                "等待 SQL waiting_query       = {}\n" +
                                "阻塞事务 blocking_trx_id    = {}\n" +
                                "阻塞线程 blocking_thread     = {}\n" +
                                "阻塞 SQL blocking_query      = {}\n" +
                                "=========================",
                        rs.getString("waiting_trx_id"),
                        rs.getString("waiting_thread"),
                        rs.getString("waiting_query"),
                        rs.getString("blocking_trx_id"),
                        rs.getString("blocking_thread"),
                        rs.getString("blocking_query")
                );
            }

            if (!found) {
                log.warn("【没有发现锁等待】（可能线程未成功进入阻塞）");
            }
        }
    }
}
