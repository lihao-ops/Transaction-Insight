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

/**
 * Spring 事务传播实验的核心服务，封装不同 Propagation 模式的调用方式与性能对比工具方法。
 */
@Service
public class PropagationLabService {

    private final ExperimentRecordRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTransactionManager transactionManager;

    /**
     * @param repository           实验记录仓储
     * @param transactionTemplate  预配置的事务模板，用于包裹批量操作
     * @param transactionManager   手动控制事务时使用的管理器
     */
    public PropagationLabService(ExperimentRecordRepository repository,
                                 TransactionTemplate transactionTemplate,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.transactionManager = transactionManager;
    }

    /**
     * 使用 {@link Propagation#REQUIRED} 的事务，常用于对比 requiresNew 的行为差异。
     *
     * @param scenario 场景标识
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void required(String scenario) {
        repository.save(new ExperimentRecord("REQUIRED-" + scenario));
    }

    /**
     * 每次调用开启新事务，模拟事件日志与主流程解耦的做法。
     *
     * @param scenario 场景标识
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNew(String scenario) {
        repository.save(new ExperimentRecord("REQUIRES_NEW-" + scenario));
    }

    /**
     * 使用单个 REQUIRED 事务包裹批量写入，用于计算在同一事务中写入的耗时。
     *
     * @param count 写入条数
     * @return 批处理耗时（毫秒）
     */
    public long batchInsertRequired(int count) {
        long start = System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                required("batch-" + i);
            }
        });
        return System.currentTimeMillis() - start;
    }

    /**
     * 逐条开启新事务写入，演示 REQUIRES_NEW 带来的额外事务开销。
     *
     * @param count 写入条数
     * @return 总耗时（毫秒）
     */
    public long batchInsertRequiresNew(int count) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            requiresNew("batch-" + i);
        }
        return System.currentTimeMillis() - start;
    }

    /**
     * 演示在同一个 Bean 内部调用带 @Transactional 方法时，由于未经过代理而不会新建事务的现象。
     *
     * @param count 调用次数
     * @return 最终记录数量
     */
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

    /**
     * 被 {@link #invokeWithoutProxy(int)} 调用的内部方法，用于验证自调用失效。
     */
    @Transactional
    public void internalTransactionalMethod() {
        repository.save(new ExperimentRecord("SELF-INVOKED"));
    }
}
