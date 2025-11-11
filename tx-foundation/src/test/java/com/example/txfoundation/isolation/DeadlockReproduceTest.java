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
 * 在可重复读隔离级别下重现死锁场景，展示两个事务交叉锁定资源导致的回滚行为。
 * Reproduce a deadlock scenario under REPEATABLE READ isolation where two transactions lock resources in opposite order.
 *
 * 预期结果 / Expected Result:
 * - 至少有一个事务因死锁或锁等待超时而回滚。
 * - 最终账户余额总和保持不变，验证数据库一致性。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tx_foundation_deadlock;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
})
public class DeadlockReproduceTest {

    private static final Logger log = LoggerFactory.getLogger(DeadlockReproduceTest.class);

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void initSchema() throws SQLException {
        log.info("Initializing schema for deadlock test / 初始化死锁测试数据表");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS deadlock_account (" +
                    "id BIGINT PRIMARY KEY, balance DECIMAL(15,2))");
        }
    }

    @BeforeEach
    void resetData() throws SQLException {
        log.info("Reset deadlock data before test / 测试前重置死锁数据");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM deadlock_account");
            statement.execute("INSERT INTO deadlock_account(id, balance) VALUES (1, 100.00)");
            statement.execute("INSERT INTO deadlock_account(id, balance) VALUES (2, 100.00)");
        }
    }

    @AfterEach
    void cleanup() {
        log.info("Deadlock test finished / 死锁测试完成");
    }

    /**
     * 测试过程 / Test Procedure:
     * 1. 事务 A 锁定账户 1 后尝试更新账户 2。
     * 2. 事务 B 锁定账户 2 后尝试更新账户 1。
     * 3. 观察死锁产生并确保至少一个事务回滚，同时余额总和保持 200。
     */
    @Test
    void shouldTriggerDeadlockAndRollbackOneTransaction() throws Exception {
        log.info("Step 0: Setup concurrency environment / 构建并发执行环境");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch txAFirstLock = new CountDownLatch(1);
        CountDownLatch txBFirstLock = new CountDownLatch(1);

        AtomicReference<Throwable> txAError = new AtomicReference<>();
        AtomicReference<Throwable> txBError = new AtomicReference<>();

        Future<?> txAFuture = executor.submit(() -> executeDeadlockTransaction(
                "Transaction A", 1L, new BigDecimal("-10.00"), 2L, new BigDecimal("10.00"),
                txAFirstLock, txBFirstLock, txAError));

        Future<?> txBFuture = executor.submit(() -> executeDeadlockTransaction(
                "Transaction B", 2L, new BigDecimal("-5.00"), 1L, new BigDecimal("5.00"),
                txBFirstLock, txAFirstLock, txBError));

        txAFuture.get(10, TimeUnit.SECONDS);
        txBFuture.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        Assertions.assertTrue(txAError.get() != null || txBError.get() != null,
                "At least one transaction should rollback due to deadlock / 至少一个事务应因死锁回滚");

        BigDecimal totalBalance = selectTotalBalance();
        Assertions.assertEquals(new BigDecimal("200.00"), totalBalance,
                "Total balance should remain 200 after deadlock handling / 死锁处理后余额总和应保持 200");
    }

    private void executeDeadlockTransaction(String name,
                                            long firstAccountId,
                                            BigDecimal firstDelta,
                                            long secondAccountId,
                                            BigDecimal secondDelta,
                                            CountDownLatch selfLatch,
                                            CountDownLatch otherLatch,
                                            AtomicReference<Throwable> errorHolder) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            log.info("{} started with REPEATABLE READ / {} 以可重复读隔离级别启动", name, name);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCK_TIMEOUT 1000");
            }
            try {
                log.info("{} Step 1: 更新账户 {} / {} 更新账户 {}", name, firstAccountId, name, firstAccountId);
                updateBalance(connection, firstAccountId, firstDelta);
                selfLatch.countDown();

                log.info("{} Step 2: 等待对方锁定完成 / {} 等待对方事务", name, name);
                await(otherLatch);

                log.info("{} Step 3: 更新账户 {}，预期发生死锁 / {} 尝试更新账户 {}", name, secondAccountId, name, secondAccountId);
                updateBalance(connection, secondAccountId, secondDelta);

                connection.commit();
                log.info("{} commit success / {} 提交成功", name, name);
            } catch (Exception ex) {
                connection.rollback();
                log.error("{} rollback due to deadlock / {} 因死锁回滚", name, name, ex);
                errorHolder.set(ex);
            }
        } catch (SQLException ex) {
            log.error("{} encountered SQL exception / {} 遇到 SQL 异常", name, name, ex);
            errorHolder.set(ex);
        }
    }

    private void updateBalance(Connection connection, long accountId, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE deadlock_account SET balance = balance + ? WHERE id = ?")) {
            ps.setBigDecimal(1, delta);
            ps.setLong(2, accountId);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException("Account not found / 未找到账户");
            }
        }
    }

    private BigDecimal selectTotalBalance() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT SUM(balance) FROM deadlock_account")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        }
        throw new IllegalStateException("Failed to calculate total balance / 无法计算余额总和");
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
}
