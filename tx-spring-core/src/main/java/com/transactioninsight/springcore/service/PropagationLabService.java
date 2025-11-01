package com.transactioninsight.springcore.service;

import com.transactioninsight.springcore.entity.ExperimentRecord;
import com.transactioninsight.springcore.entity.ExperimentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PropagationLabService {

    private final ExperimentRecordRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTransactionManager transactionManager;

    public PropagationLabService(ExperimentRecordRepository repository,
                                 TransactionTemplate transactionTemplate,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.transactionManager = transactionManager;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void required(String scenario) {
        repository.save(new ExperimentRecord("REQUIRED-" + scenario));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNew(String scenario) {
        repository.save(new ExperimentRecord("REQUIRES_NEW-" + scenario));
    }

    public long batchInsertRequired(int count) {
        long start = System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                required("batch-" + i);
            }
        });
        return System.currentTimeMillis() - start;
    }

    public long batchInsertRequiresNew(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            requiresNew("batch-" + i);
        }
        return System.currentTimeMillis() - start;
    }

    public int invokeWithoutProxy(int count) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            for (int i = 0; i < count; i++) {
                internalTransactionalMethod();
            }
            transactionManager.commit(status);
            return repository.findAll().size();
        } catch (RuntimeException ex) {
            transactionManager.rollback(status);
            throw ex;
        }
    }

    @Transactional
    public void internalTransactionalMethod() {
        repository.save(new ExperimentRecord("SELF-INVOKED"));
    }
}
