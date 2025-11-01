package com.transactioninsight.distributed;

import com.transactioninsight.distributed.tcc.AccountTccService;
import com.transactioninsight.distributed.tcc.TccTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TccTransactionManagerTest {

    @Autowired
    private TccTransactionManager manager;

    @Autowired
    private AccountTccService accountService;

    @Test
    void confirmFlowReleasesFrozenFunds() {
        String globalTxId = "tx-1";
        manager.begin(globalTxId);
        manager.registerBranch(globalTxId, accountService);
        accountService.tryAction();

        manager.commit(globalTxId);

        assertThat(accountService.frozenBalance(1L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

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
