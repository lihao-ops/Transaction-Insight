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

/**
 * 测试目标：验证 {@link MvccVisualizer} 在 REPEATABLE_READ 中返回的快照余额始终等于初始值。
 * 事务知识点：多版本并发控制（MVCC）如何为快照读提供可重复读的隔离级别。
 * 说明：在其他事务可能更新数据的情况下读取快照，确认余额保持 300，符合 MVCC 的设计。
 */
@SpringBootTest
class MvccVisualizerTest {

    @Autowired
    private MvccVisualizer visualizer;

    @Autowired
    private AccountRepository repository;

    /**
     * 准备基线账户数据，模拟单条记录的 MVCC 行为。
     */
    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(new Account(new BigDecimal("300")));
    }

    /**
     * 核心断言：{@link MvccVisualizer#demonstrateMvcc(Long)} 返回的余额保持事务快照值 300。
     */
    @Test
    void snapshotReadKeepsOriginalBalance() {
        MvccVisualizer.SnapshotResult result = visualizer.demonstrateMvcc(repository.findAll().get(0).getId());
        assertThat(result.balance()).isEqualByComparingTo("300");
    }
}
