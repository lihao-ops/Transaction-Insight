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
