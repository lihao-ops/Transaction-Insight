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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试目标 / Test Objective:
 * 展示 Serializable 隔离级别下的严格锁控制，验证事务 B 在事务 A 持有锁期间无法更新同一行数据。
 * Demonstrate strict locking under SERIALIZABLE isolation where Transaction B cannot update a locked row by Transaction A.
 *
 * 预期结果 / Expected Result:
 * - 事务 B 在事务 A 提交前被阻塞，执行时间明显延长。
 * - 事务 A 提交后，事务 B 才能成功更新并提交。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tx_foundation_serializable;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
})
public class SerializableLockingTest {

    private static final Logger log = LoggerFactory.getLogger(SerializableLockingTest.class);

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void initSchema() throws SQLException {
        log.info("Initializing schema for Serializable test / 初始化 SERIALIZABLE 测试数据表");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS inventory_lock (" +
                    "id BIGINT PRIMARY KEY, product VARCHAR(64), stock INT)");
        }
    }

    @BeforeEach
    void resetData() throws SQLException {
        log.info("Reset inventory data before test / 测试前重置库存数据");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM inventory_lock");
            statement.execute("INSERT INTO inventory_lock(id, product, stock) VALUES (1, 'BOOK', 20)");
        }
    }

    @AfterEach
    void cleanup() {
        log.info("Serializable test finished / SERIALIZABLE 测试完成");
    }

    /**
     * 测试过程 / Test Procedure:
     * 1. 事务 A 在 SERIALIZABLE 隔离级别下通过 SELECT FOR UPDATE 持有库存行锁。
     * 2. 事务 B 尝试更新同一行，将被阻塞直到事务 A 提交。
     * 3. 记录事务 B 执行时间，验证锁等待行为。
     */
    @Test
    void shouldBlockConcurrentUpdateUntilLockReleased() throws Exception {
        log.info("Step 0: Prepare executor and synchronization tools / 准备线程池和同步工具");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch updateAttempted = new CountDownLatch(1);

        AtomicLong beforeUpdateTime = new AtomicLong();
        AtomicLong afterUpdateTime = new AtomicLong();

        Future<?> txAFuture = executor.submit(() -> runInTransaction("Transaction A", Connection.TRANSACTION_SERIALIZABLE, connection -> {
            log.info("Step 1: 事务 A 通过 SELECT FOR UPDATE 锁定库存 / Transaction A locks inventory via SELECT FOR UPDATE");
            try (PreparedStatement ps = connection.prepareStatement("SELECT stock FROM inventory_lock WHERE id = ? FOR UPDATE")) {
                ps.setLong(1, 1L);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        log.info("Transaction A locked stock value: {} / 事务 A 锁定库存值", rs.getInt(1));
                    }
                }
            }
            lockAcquired.countDown();

            log.info("Step 1.1: 等待事务 B 发起更新请求 / Waiting for Transaction B update request");
            await(updateAttempted);

            log.info("Step 1.2: 保持锁 300ms 后提交释放锁 / Hold lock for 300ms before committing");
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Transaction A sleep interrupted / 事务 A 休眠被中断", ex);
            }
        }));

        Future<?> txBFuture = executor.submit(() -> runInTransaction("Transaction B", Connection.TRANSACTION_SERIALIZABLE, connection -> {
            log.info("Step 2: 等待事务 A 锁定库存 / Wait for Transaction A to lock row");
            await(lockAcquired);

            log.info("Step 2.1: 事务 B 申请更新库存 / Transaction B requests to update stock");
            beforeUpdateTime.set(System.nanoTime());
            updateAttempted.countDown();

            try (PreparedStatement ps = connection.prepareStatement("UPDATE inventory_lock SET stock = stock - 5 WHERE id = ?")) {
                ps.setLong(1, 1L);
                ps.executeUpdate();
            }
            afterUpdateTime.set(System.nanoTime());
            log.info("Step 2.2: 事务 B 更新完成并准备提交 / Transaction B finished update and ready to commit");
        }));

        txAFuture.get(10, TimeUnit.SECONDS);
        txBFuture.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        long durationMillis = Duration.ofNanos(afterUpdateTime.get() - beforeUpdateTime.get()).toMillis();
        log.info("Transaction B wait duration: {} ms / 事务 B 等待时长", durationMillis);
        Assertions.assertTrue(durationMillis >= 300,
                "Transaction B should wait until Transaction A releases lock / 事务 B 应等待事务 A 释放锁");

        Assertions.assertEquals(15, selectStock(), "Final stock should be reduced to 15 / 最终库存应减少至 15");
    }

    private int selectStock() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT stock FROM inventory_lock WHERE id = ?")) {
            ps.setLong(1, 1L);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Inventory not found / 未找到库存记录");
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

    @FunctionalInterface
    private interface TransactionCallback {
        void doInTransaction(Connection connection) throws Exception;
    }
}
