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
 * 类说明 / Class Description:
 * 中文：死锁复现服务，通过并发事务交叉更新模拟 InnoDB 死锁检测与回滚行为。
 * English: Deadlock reproduction service simulating InnoDB deadlock detection and rollback via cross-updates.
 *
 * 使用场景 / Use Cases:
 * 中文：教学演示并发写导致的循环等待与死锁处理策略。
 * English: Educational demo for circular waits and deadlock handling under concurrent writes.
 *
 * 设计目的 / Design Purpose:
 * 中文：构造可控并发场景，直观观察事务冲突与异常处理。
 * English: Build controllable concurrency scenarios to observe transaction conflicts and exception handling.
 */
@Service
public class DeadlockReproductionService {

    private static final Logger log = LoggerFactory.getLogger(DeadlockReproductionService.class);
    private final AccountRepository repository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 方法说明 / Method Description:
     * 中文：构造服务并注入仓储与事务管理器。
     * English: Construct service injecting repository and transaction manager.
     *
     * 参数 / Parameters:
     * @param repository 中文说明：账户数据访问仓储
     *                   English description: Account data access repository
     * @param transactionManager 中文说明：事务开启/提交/回滚的管理器
     *                           English description: Manager for starting/committing/rolling back transactions
     *
     * 返回值 / Return:
     * 中文说明：服务实例
     * English description: Service instance
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    public DeadlockReproductionService(AccountRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    /**
     * 方法说明 / Method Description:
     * 中文：并行启动两个事务反向转账，制造循环等待以触发死锁。
     * English: Start two transactions in parallel for reverse transfers to create circular wait and trigger deadlock.
     *
     * 参数 / Parameters:
     * @param firstAccount 中文说明：第一个事务锁定的账户 ID
     *                     English description: Account ID locked by the first transaction
     * @param secondAccount 中文说明：第二个事务锁定的账户 ID
     *                      English description: Account ID locked by the second transaction
     *
     * 返回值 / Return:
     * 中文说明：无
     * English description: None
     *
     * 异常 / Exceptions:
     * 中文/英文：并发更新失败或死锁将通过日志记录并由回滚处理
     *
     * 逻辑概述 / Logic Overview:
     * 中文：定义事务级别为可重复读，使用 CompletableFuture 并行提交两个转账任务，然后等待完成。
     * English: Set isolation to REPEATABLE_READ, submit two transfer tasks in parallel via CompletableFuture, then await completion.
     */
    public void reproduceDeadlock(Long firstAccount, Long secondAccount) {
        // 中文：声明事务定义并设置隔离级别
        // English: Declare transaction definition and set isolation level
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        // 中文：并行提交两个转账任务以制造循环等待
        // English: Submit two transfer tasks in parallel to create circular wait
        CompletableFuture<Void> tx1 = CompletableFuture.runAsync(() -> transfer(definition, firstAccount, secondAccount));
        CompletableFuture<Void> tx2 = CompletableFuture.runAsync(() -> transfer(definition, secondAccount, firstAccount));

        // 中文：等待两个任务完成
        // English: Wait for both tasks to complete
        CompletableFuture.allOf(tx1, tx2).join();
    }

    /**
     * 方法说明 / Method Description:
     * 中文：在给定事务定义下执行转账并处理可能的死锁异常。
     * English: Execute a transfer under given transaction definition and handle potential deadlock exceptions.
     *
     * 参数 / Parameters:
     * @param definition 中文说明：事务定义（隔离级别等）
     *                   English description: Transaction definition (isolation, etc.)
     * @param debitId    中文说明：扣款账户 ID
     *                   English description: Debit account ID
     * @param creditId   中文说明：收款账户 ID
     *                   English description: Credit account ID
     *
     * 返回值 / Return:
     * 中文说明：无
     * English description: None
     *
     * 异常 / Exceptions:
     * 中文/英文：出现死锁或并发冲突时记录日志并回滚
     */
    private void transfer(DefaultTransactionDefinition definition, Long debitId, Long creditId) {
        // 中文：开启事务并获取事务状态
        // English: Begin transaction and obtain status
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            // 中文：加载转账双方账户记录
            // English: Load both account records for transfer
            Account debit = repository.findById(debitId).orElseThrow();
            Account credit = repository.findById(creditId).orElseThrow();

            // 中文：相同记录不同顺序的更新，形成锁冲突根源
            // English: Update same records in different orders causing lock conflicts
            debit.setBalance(debit.getBalance().subtract(BigDecimal.ONE));
            credit.setBalance(credit.getBalance().add(BigDecimal.ONE));
            log.debug("Transfer executed by {}", Thread.currentThread().getName());

            // 中文：正常提交事务
            // English: Commit transaction normally
            transactionManager.commit(status);
        } catch (Exception ex) {
            // 中文：记录死锁异常并回滚事务
            // English: Log deadlock exception and roll back transaction
            log.warn("Deadlock detected in {}", Thread.currentThread().getName(), ex);
            transactionManager.rollback(status);
        }
    }
}
