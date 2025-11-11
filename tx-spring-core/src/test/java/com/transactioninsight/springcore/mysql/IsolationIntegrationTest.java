package com.transactioninsight.springcore.mysql;

import com.transactioninsight.springcore.mysql.service.AccountService;
import com.transactioninsight.springcore.mysql.service.AccountService.AccountSeed;
import com.transactioninsight.springcore.mysql.service.TransactionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目标：通过并发读写模拟 READ_COMMITTED 与 REPEATABLE_READ 下的隔离差异。
 * 事务知识点：隔离性（Isolation）中“不可重复读”的出现与避免条件。
 * 说明：两个线程分别模拟读事务与写事务，验证 READ_COMMITTED 会看到更新，而 REPEATABLE_READ 能维持快照。
 */
class IsolationIntegrationTest extends AbstractMySqlTransactionIntegrationTest {

    private static final Long ALICE = 1L;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionExecutor transactionExecutor;

    @BeforeEach
    void setUp() {
        accountService.resetAccounts(List.of(
                new AccountSeed(ALICE, "Alice", new BigDecimal("120.00"))
        ));
    }

    @Test
    void readCommittedAllowsNonRepeatableRead() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<List<BigDecimal>> reader = executor.submit(() -> transactionExecutor.execute(
                TransactionDefinition.ISOLATION_READ_COMMITTED,
                true,
                () -> {
                    BigDecimal first = accountService.getBalance(ALICE);
                    firstRead.countDown();
                    writerCommitted.await();
                    BigDecimal second = accountService.getBalance(ALICE);
                    return List.of(first, second);
                }
        ));

        Future<Void> writer = executor.submit(() -> {
            firstRead.await();
            transactionExecutor.executeWithoutResult(
                    TransactionDefinition.ISOLATION_READ_COMMITTED,
                    false,
                    () -> accountService.deposit(ALICE, new BigDecimal("30.00"))
            );
            writerCommitted.countDown();
            return null;
        });

        List<BigDecimal> results = reader.get();
        writer.get();
        executor.shutdown();

        assertThat(results.get(0)).isNotEqualByComparingTo(results.get(1));
    }

    @Test
    void repeatableReadPreventsNonRepeatableRead() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<List<BigDecimal>> reader = executor.submit(() -> transactionExecutor.execute(
                TransactionDefinition.ISOLATION_REPEATABLE_READ,
                true,
                () -> {
                    BigDecimal first = accountService.getBalance(ALICE);
                    firstRead.countDown();
                    writerCommitted.await();
                    BigDecimal second = accountService.getBalance(ALICE);
                    return List.of(first, second);
                }
        ));

        Future<Void> writer = executor.submit(() -> {
            firstRead.await();
            transactionExecutor.executeWithoutResult(
                    TransactionDefinition.ISOLATION_READ_COMMITTED,
                    false,
                    () -> accountService.deposit(ALICE, new BigDecimal("40.00"))
            );
            writerCommitted.countDown();
            return null;
        });

        List<BigDecimal> results = reader.get();
        writer.get();
        executor.shutdown();

        assertThat(results.get(0)).isEqualByComparingTo(results.get(1));
    }
}
