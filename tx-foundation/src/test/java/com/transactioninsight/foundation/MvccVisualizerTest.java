package com.transactioninsight.foundation;

import com.transactioninsight.foundation.mvcc.MvccVisualizer;
import com.transactioninsight.foundation.mysql.Account;
import com.transactioninsight.foundation.mysql.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MvccVisualizerTest {

    @Autowired
    private MvccVisualizer visualizer;

    @Autowired
    private AccountRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(new Account(new BigDecimal("300")));
    }

    @Test
    void snapshotReadKeepsOriginalBalance() {
        MvccVisualizer.SnapshotResult result = visualizer.demonstrateMvcc(repository.findAll().get(0).getId());
        assertThat(result.balance()).isEqualByComparingTo("300");
    }
}
