package com.transactioninsight.springcore.mysql;

import com.transactioninsight.springcore.mysql.service.AccountService;
import com.transactioninsight.springcore.mysql.service.AccountService.AccountSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目标：在高并发转账下验证资金总额保持不变。
 * 事务知识点：一致性（Consistency）与约束保持，强调业务层事务保证守恒。
 * 说明：多线程执行 transfer 操作，断言总余额未变化且各账户余额不为负值。
 */
class ConsistencyIntegrationTest extends AbstractMySqlTransactionIntegrationTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;
    private static final Long CHARLIE = 3L;

    @Autowired
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService.resetAccounts(List.of(
                new AccountSeed(ALICE, "Alice", new BigDecimal("200.00")),
                new AccountSeed(BOB, "Bob", new BigDecimal("150.00")),
                new AccountSeed(CHARLIE, "Charlie", new BigDecimal("50.00"))
        ));
    }

    @Test
    void concurrentTransfersKeepTotalBalanceStable() throws Exception {
        BigDecimal totalBefore = accountService.totalBalance();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Callable<Void>> tasks = new ArrayList<>();
        BigDecimal transferAmount = new BigDecimal("5.00");

        for (int i = 0; i < 60; i++) {
            long from = switch (i % 3) {
                case 0 -> ALICE;
                case 1 -> BOB;
                default -> CHARLIE;
            };
            long to = switch (i % 3) {
                case 0 -> BOB;
                case 1 -> CHARLIE;
                default -> ALICE;
            };
            tasks.add(() -> {
                accountService.transfer(from, to, transferAmount);
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(accountService.totalBalance()).isEqualByComparingTo(totalBefore);
        assertThat(accountService.getBalance(ALICE)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(accountService.getBalance(BOB)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(accountService.getBalance(CHARLIE)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
