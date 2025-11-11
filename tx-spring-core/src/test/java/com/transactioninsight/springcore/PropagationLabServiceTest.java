package com.transactioninsight.springcore;

import com.transactioninsight.springcore.entity.ExperimentRecordRepository;
import com.transactioninsight.springcore.service.PropagationLabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目标：验证事务传播实验服务的性能对比与自调用特性。
 * 事务知识点：Spring 事务传播行为（REQUIRED、REQUIRES_NEW）以及自调用导致的代理失效。
 * 说明：比较不同传播级别的批量插入耗时，并验证未经过代理的自调用不会开启新事务。
 */
@SpringBootTest
class PropagationLabServiceTest {

    @Autowired
    private PropagationLabService service;

    @Autowired
    private ExperimentRecordRepository repository;

    /**
     * 每次测试前清空表，确保统计结果互不影响。
     */
    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    /**
     * 测试 REQUIRED 与 REQUIRES_NEW 的耗时差异及累计写入数量。
     */
    @Test
    void requiredIsFasterThanRequiresNewForBatchOperations() {
        long required = service.batchInsertRequired(10);
        long requiresNew = service.batchInsertRequiresNew(10);

        assertThat(required).isLessThan(requiresNew + 50);
        assertThat(repository.count()).isEqualTo(20);
    }

    /**
     * 测试自调用场景下不会触发新的事务代理，记录数与调用次数一致。
     */
    @Test
    @Transactional
    void selfInvocationDoesNotStartNewTransaction() {
        int records = service.invokeWithoutProxy(3);
        assertThat(records).isEqualTo(3);
    }
}
