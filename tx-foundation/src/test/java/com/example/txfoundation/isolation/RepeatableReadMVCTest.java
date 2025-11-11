package com.example.txfoundation.isolation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试目标 / Test Objective:
 * 验证 Repeatable Read 隔离级别下，事务 A 是否可以重复读取同一版本数据，实现 MVCC 的一致视图。
 * Verify whether Transaction A observes a consistent snapshot with Repeatable Read isolation using MVCC.
 *
 * 预期结果 / Expected Result:
 * - 第一次读取：读取到旧值。
 * - 第二次读取：即使事务 B 提交更新，也应读取到旧值，验证快照隔离。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tx_foundation_rr;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
})
public class RepeatableReadMVCTest {

    private static final Logger log = LoggerFactory.getLogger(RepeatableReadMVCTest.class);

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void initSchema() throws SQLException {
        log.info("Initializing schema for Repeatable Read test / 初始化 REPEATABLE READ 测试数据表");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS order_snapshot (" +
                    "id BIGINT PRIMARY KEY, amount DECIMAL(15,2), version INT)");
        }
    }

    @BeforeEach
    void resetData() throws SQLException {
        log.info("Resetting snapshot data before test / 测试前重置快照数据");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM order_snapshot");
            statement.execute("INSERT INTO order_snapshot(id, amount, version) VALUES (1, 50.00, 0)");
        }
    }

    @AfterEach
    void cleanup() {
        log.info("Repeatable Read test finished / Repeatable Read 测试完成");
    }

    /**
     * 测试过程 / Test Procedure:
     * 1. 事务 A 在 Repeatable Read 隔离级别下读取订单金额。
     * 2. 事务 B 更新金额并提交。
     * 3. 事务 A 再次读取，验证仍为旧值，体现 MVCC 快照一致性。
     */
    @Test
    void shouldMaintainSnapshotAcrossReads() throws Exception {
        log.info("Step 0: Setup concurrency controls / 设置并发控制器");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstReadDone = new CountDownLatch(1);
        CountDownLatch txBFinished = new CountDownLatch(1);

        AtomicReference<BigDecimal> firstRead = new AtomicReference<>();
        AtomicReference<BigDecimal> secondRead = new AtomicReference<>();

        Future<?> txAFuture = executor.submit(() -> runInTransaction("Transaction A", Connection.TRANSACTION_REPEATABLE_READ, connection -> {
            log.info("Step 1: 事务 A 首次读取 / Transaction A performs first read");
            firstRead.set(selectAmount(connection));
            log.info("Transaction A first read amount: {} / 事务 A 第一次读取金额", firstRead.get());
            firstReadDone.countDown();

            log.info("Step 3: 等待事务 B 提交更新 / Wait for Transaction B to finish");
            await(txBFinished);

            log.info("Step 4: 事务 A 再次读取应保持旧值 / Transaction A second read should keep old value");
            secondRead.set(selectAmount(connection));
            log.info("Transaction A second read amount: {} / 事务 A 第二次读取金额", secondRead.get());
        }));

        Future<?> txBFuture = executor.submit(() -> runInTransaction("Transaction B", Connection.TRANSACTION_READ_COMMITTED, connection -> {
            log.info("Step 2: 等待事务 A 完成首次读取 / Wait for Transaction A first read");
            await(firstReadDone);

            log.info("Step 2.1: 事务 B 更新金额至 120.00 / Transaction B updates amount to 120.00");
            try (PreparedStatement ps = connection.prepareStatement("UPDATE order_snapshot SET amount = ?, version = version + 1 WHERE id = ?")) {
                ps.setBigDecimal(1, new BigDecimal("120.00"));
                ps.setLong(2, 1L);
                ps.executeUpdate();
            }

            log.info("Step 2.2: 事务 B 提交并通知 / Transaction B commits and notifies");
            txBFinished.countDown();
        }));

        txAFuture.get(10, TimeUnit.SECONDS);
        txBFuture.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        Assertions.assertEquals(new BigDecimal("50.00"), firstRead.get(), "First read should see original amount / 第一次读取应看到原金额");
        Assertions.assertEquals(new BigDecimal("50.00"), secondRead.get(), "Second read should retain snapshot / 第二次读取应保留快照值");
    }

    private void runInTransaction(String name, int isolation, TransactionCallback callback) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(isolation);
            log.info("{} started with isolation {} / {} 以隔离级别 {} 启动", name, isolation, name, isolation);
            try {
                callback.doInTransaction(connection);
                connection.commit();
                log.info("{} commit success / {} 提交成功", name, name);
            } catch (Exception ex) {
                connection.rollback();
                log.error("{} rollback due to exception / {} 因异常回滚", name, name, ex);
                throw new IllegalStateException(ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("Latch wait timeout / 等待超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private BigDecimal selectAmount(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT amount FROM order_snapshot WHERE id = ?")) {
            ps.setLong(1, 1L);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        }
        throw new IllegalStateException("Order not found / 未找到订单");
    }

    @FunctionalInterface
    private interface TransactionCallback {
        void doInTransaction(Connection connection) throws Exception;
    }
}
