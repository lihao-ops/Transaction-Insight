package com.transactioninsight.distributed;

import com.transactioninsight.distributed.tcc.AccountTccService;
import com.transactioninsight.distributed.tcc.TccTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试目标：验证 TCC 管理器在 confirm/rollback 阶段对冻结金额的处理符合预期。
 * 事务知识点：分布式事务的补偿事务模式（TCC），强调 Try 阶段资源冻结与后续解冻。
 * 说明：模拟 commit 与 rollback 两种路径，断言冻结资金被释放，体现分支事务的一致性保障。
 */
@SpringBootTest
class TccTransactionManagerTest {

    @Autowired
    private TccTransactionManager manager;

    @Autowired
    private AccountTccService accountService;

    /**
     * 验证 confirm 流程会调用 {@link AccountTccService#release(Long, BigDecimal)}，冻结金额清零。
     */
    @Test
    void confirmFlowReleasesFrozenFunds() {
        String globalTxId = "tx-1";
        manager.begin(globalTxId);
        manager.registerBranch(globalTxId, accountService);
        accountService.tryAction();

        manager.commit(globalTxId);

        assertThat(accountService.frozenBalance(1L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * 验证 rollback 流程会调用 {@link AccountTccService#thaw(Long)}，恢复 Try 阶段冻结的金额。
     */
    @Test
    void rollbackFlowThawsFrozenFunds() {
        String globalTxId = "tx-rollback";
        manager.begin(globalTxId);
        manager.registerBranch(globalTxId, accountService);
        accountService.tryAction();

        manager.rollback(globalTxId);

        assertThat(accountService.frozenBalance(1L)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
