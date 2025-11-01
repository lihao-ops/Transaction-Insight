package com.transactioninsight.springcore;

import com.transactioninsight.springcore.entity.ExperimentRecordRepository;
import com.transactioninsight.springcore.service.PropagationLabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PropagationLabServiceTest {

    @Autowired
    private PropagationLabService service;

    @Autowired
    private ExperimentRecordRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    @Test
    void requiredIsFasterThanRequiresNewForBatchOperations() {
        long required = service.batchInsertRequired(10);
        long requiresNew = service.batchInsertRequiresNew(10);

        assertThat(required).isLessThan(requiresNew + 50);
        assertThat(repository.count()).isEqualTo(20);
    }

    @Test
    @Transactional
    void selfInvocationDoesNotStartNewTransaction() {
        int records = service.invokeWithoutProxy(3);
        assertThat(records).isEqualTo(3);
    }
}
