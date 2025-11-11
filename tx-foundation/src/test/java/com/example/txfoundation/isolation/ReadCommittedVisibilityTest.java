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
 * 验证 Read Committed 隔离级别下，事务 A 能否在事务 B 提交后读取到最新数据。
 * Verify whether Transaction A can observe committed changes from Transaction B under READ COMMITTED.
 *
 * 预期结果 / Expected Result:
 * - 第一次读取：事务 A 在事务 B 更新之前无法读取到新数据。
 * - 第二次读取：事务 B 提交后，事务 A 能读取到最新数据。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tx_foundation_rc;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
})
public class ReadCommittedVisibilityTest {

    private static final Logger log = LoggerFactory.getLogger(ReadCommittedVisibilityTest.class);

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void initSchema() throws SQLException {
        log.info("Initializing schema for Read Committed test / 初始化 READ COMMITTED 测试数据表");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS account_balance (" +
                    "id BIGINT PRIMARY KEY, balance DECIMAL(15,2), version INT)");
        }
    }

    @BeforeEach
    void resetData() throws SQLException {
        log.info("Resetting data before test / 测试前重置数据");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM account_balance");
            statement.execute("INSERT INTO account_balance(id, balance, version) VALUES (1, 100.00, 0)");
        }
    }

    @AfterEach
    void cleanupConnections() {
        log.info("Test finished, resources released / 测试结束释放资源");
    }

    /**
     * 测试过程 / Test Procedure:
     * 1. 事务 A 按 READ COMMITTED 隔离级别开启并读取初始余额。
     * 2. 事务 B 更新余额并提交。
     * 3. 事务 A 再次读取，验证是否看到事务 B 的提交。
     */
    @Test
    void shouldSeeCommittedChangesAfterSecondRead() throws Exception {
        log.info("Step 0: Prepare executor and latches / 准备线程池和同步信号");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstReadDone = new CountDownLatch(1);
        CountDownLatch txBCommitted = new CountDownLatch(1);

        AtomicReference<BigDecimal> firstRead = new AtomicReference<>();
        AtomicReference<BigDecimal> secondRead = new AtomicReference<>();

        Future<?> txAFuture = executor.submit(() -> runInTransaction("Transaction A", Connection.TRANSACTION_READ_COMMITTED, connection -> {
            log.info("Step 1: 开启事务 A，首次读取 / Begin Transaction A, first read");
            firstRead.set(selectBalance(connection));
            log.info("Transaction A first read result: {} / 事务 A 第一次读取结果", firstRead.get());
            firstReadDone.countDown();

            log.info("Step 3: 等待事务 B 提交 / Wait for Transaction B to commit");
            await(txBCommitted);

            log.info("Step 4: 事务 B 提交后再次读取 / Transaction A reads again after B commit");
            secondRead.set(selectBalance(connection));
            log.info("Transaction A second read result: {} / 事务 A 第二次读取结果", secondRead.get());
        }));

        Future<?> txBFuture = executor.submit(() -> runInTransaction("Transaction B", Connection.TRANSACTION_READ_COMMITTED, connection -> {
            log.info("Step 2: 等待事务 A 第一次读取完成 / Wait for Transaction A to complete first read");
            await(firstReadDone);

            log.info("Step 2.1: 事务 B 更新余额至 200.00 / Transaction B updates balance to 200.00");
            try (PreparedStatement ps = connection.prepareStatement("UPDATE account_balance SET balance = ?, version = version + 1 WHERE id = ?")) {
                ps.setBigDecimal(1, new BigDecimal("200.00"));
                ps.setLong(2, 1L);
                ps.executeUpdate();
            }

            log.info("Step 2.2: 事务 B 提交 / Commit Transaction B");
            txBCommitted.countDown();
        }));

        txAFuture.get(10, TimeUnit.SECONDS);
        txBFuture.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        Assertions.assertEquals(new BigDecimal("100.00"), firstRead.get(), "First read should see original balance / 第一次读取应看到原始余额");
        Assertions.assertEquals(new BigDecimal("200.00"), secondRead.get(), "Second read should see updated balance / 第二次读取应看到更新后的余额");
    }

    private void runInTransaction(String name, int isolationLevel, TransactionCallback callback) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(isolationLevel);
            log.info("{} started with isolation {} / {} 在隔离级别 {} 下启动", name, isolationLevel, name, isolationLevel);
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private BigDecimal selectBalance(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM account_balance WHERE id = ?")) {
            ps.setLong(1, 1L);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        }
        throw new IllegalStateException("Record not found / 未找到记录");
    }

    @FunctionalInterface
    private interface TransactionCallback {
        void doInTransaction(Connection connection) throws Exception;
    }
}
