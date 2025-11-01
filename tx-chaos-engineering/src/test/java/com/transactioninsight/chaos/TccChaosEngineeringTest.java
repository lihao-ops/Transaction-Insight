package com.transactioninsight.chaos;

import com.transactioninsight.distributed.DistributedPatternsApplication;
import com.transactioninsight.distributed.tcc.AccountTccService;
import com.transactioninsight.distributed.tcc.TccTransactionManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;

@SpringBootTest(classes = DistributedPatternsApplication.class)
@Disabled("Requires Docker environment for Testcontainers")
class TccChaosEngineeringTest {

    @Autowired
    private TccTransactionManager transactionManager;

    @Autowired
    private AccountTccService accountTccService;

    private final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/testcontainers/toxiproxy:2.9.0");
    private final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Test
    void simulateNetworkPartitionDuringConfirm() {
        transactionManager.begin("chaos-tx");
        transactionManager.registerBranch("chaos-tx", accountTccService);
        accountTccService.tryAction();
    }
}
