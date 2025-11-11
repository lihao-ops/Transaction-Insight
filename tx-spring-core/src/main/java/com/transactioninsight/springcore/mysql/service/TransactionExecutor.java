package com.transactioninsight.springcore.mysql.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.function.Supplier;

@Component
public class TransactionExecutor {

    private final PlatformTransactionManager transactionManager;

    public TransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public <T> T execute(int isolationLevel, boolean readOnly, Supplier<T> action) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        definition.setIsolationLevel(isolationLevel);
        definition.setReadOnly(readOnly);

        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            T result = action.get();
            transactionManager.commit(status);
            return result;
        } catch (RuntimeException | Error ex) {
            transactionManager.rollback(status);
            throw ex;
        }
    }

    public void executeWithoutResult(int isolationLevel, boolean readOnly, Runnable action) {
        execute(isolationLevel, readOnly, () -> {
            action.run();
            return null;
        });
    }
}
