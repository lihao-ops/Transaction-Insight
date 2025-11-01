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

/**
 * 测试目标：为 TCC 方案引入网络故障/数据库故障的混沌实验模板。
 * 由于依赖 Testcontainers 与 Docker，本测试默认禁用；若启用预期会模拟确认阶段的网络分区。
 */
@SpringBootTest(classes = DistributedPatternsApplication.class)
@Disabled("Requires Docker environment for Testcontainers")
class TccChaosEngineeringTest {

    @Autowired
    private TccTransactionManager transactionManager;

    @Autowired
    private AccountTccService accountTccService;

    private final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/testcontainers/toxiproxy:2.9.0");
    private final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    /**
     * 预期：在网络分区下 confirm 阶段会失败并触发回滚逻辑。
     * 实际：默认禁用，如需执行请参考模块 README 使用本地容器或替换为手工故障注入。
     */
    @Test
    void simulateNetworkPartitionDuringConfirm() {
        transactionManager.begin("chaos-tx");
        transactionManager.registerBranch("chaos-tx", accountTccService);
        accountTccService.tryAction();
    }
}
