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

@Service
public class DeadlockReproductionService {

    private static final Logger log = LoggerFactory.getLogger(DeadlockReproductionService.class);
    private final AccountRepository repository;
    private final PlatformTransactionManager transactionManager;

    public DeadlockReproductionService(AccountRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionManager = transactionManager;
    }

    public void reproduceDeadlock(Long firstAccount, Long secondAccount) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        CompletableFuture<Void> tx1 = CompletableFuture.runAsync(() -> transfer(definition, firstAccount, secondAccount));
        CompletableFuture<Void> tx2 = CompletableFuture.runAsync(() -> transfer(definition, secondAccount, firstAccount));

        CompletableFuture.allOf(tx1, tx2).join();
    }

    private void transfer(DefaultTransactionDefinition definition, Long debitId, Long creditId) {
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            Account debit = repository.findById(debitId).orElseThrow();
            Account credit = repository.findById(creditId).orElseThrow();

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
