package com.transactioninsight.foundation.deadlock;

import com.transactioninsight.foundation.mysql.Account;
import com.transactioninsight.foundation.mysql.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * 死锁复现服务：通过两个并发事务交叉更新账户记录，帮助观察 InnoDB 死锁检测与回滚行为。
 */
@Service
public class DeadlockReproductionService {

    private static final Logger log = LoggerFactory.getLogger(DeadlockReproductionService.class);
    private final AccountRepository repository;
    private final PlatformTransactionManager transactionManager;

    /**
     * @param repository          提供账户数据的仓储
     * @param transactionManager  手动开启/提交事务的管理器
     */
    public DeadlockReproductionService(AccountRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    /**
     * 同时启动两个事务并反向转账，触发互相等待，最终模拟出死锁。
     *
     * @param firstAccount  第一个事务锁定的账户
     * @param secondAccount 第二个事务锁定的账户
     */
    public void reproduceDeadlock(Long firstAccount, Long secondAccount) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        // 核心逻辑：使用 CompletableFuture 并行提交两个转账任务以制造循环等待。
        CompletableFuture<Void> tx1 = CompletableFuture.runAsync(() -> transfer(definition, firstAccount, secondAccount));
        CompletableFuture<Void> tx2 = CompletableFuture.runAsync(() -> transfer(definition, secondAccount, firstAccount));

        CompletableFuture.allOf(tx1, tx2).join();
    }

    /**
     * 在指定事务定义下执行一次转账操作，并记录死锁时的异常信息。
     */
    private void transfer(DefaultTransactionDefinition definition, Long debitId, Long creditId) {
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            Account debit = repository.findById(debitId).orElseThrow();
            Account credit = repository.findById(creditId).orElseThrow();

            // 核心代码：相同记录不同顺序的更新，是触发死锁的根源。
            debit.setBalance(debit.getBalance().subtract(BigDecimal.ONE));
            credit.setBalance(credit.getBalance().add(BigDecimal.ONE));
            log.debug("Transfer executed by {}", Thread.currentThread().getName());

            transactionManager.commit(status);
        } catch (Exception ex) {
            log.warn("Deadlock detected in {}", Thread.currentThread().getName(), ex);
            transactionManager.rollback(status);
        }
    }
}
