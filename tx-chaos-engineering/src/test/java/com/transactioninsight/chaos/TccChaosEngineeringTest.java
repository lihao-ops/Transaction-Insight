package com.transactioninsight.chaos;

import com.transactioninsight.distributed.DistributedPatternsApplication;
import com.transactioninsight.distributed.tcc.AccountTccService;
import com.transactioninsight.distributed.tcc.TccTransactionManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 测试目标：为 TCC 方案引入网络故障/数据库故障的混沌实验模板。
 * 示例默认禁用，推荐结合真实环境或手工注入故障再执行，以观察 Confirm 阶段的补偿逻辑。
 */
@SpringBootTest(classes = DistributedPatternsApplication.class)
@Disabled("Requires manual fault injection or external chaos tooling")
class TccChaosEngineeringTest {

    @Autowired
    private TccTransactionManager transactionManager;

    @Autowired
    private AccountTccService accountTccService;

    /**
     * 预期：在网络分区下 confirm 阶段会失败并触发回滚逻辑。
     * 实际：默认禁用，如需执行请参考模块 README 使用手工故障注入或外部混沌平台。
     */
    @Test
    void simulateNetworkPartitionDuringConfirm() {
        transactionManager.begin("chaos-tx");
        transactionManager.registerBranch("chaos-tx", accountTccService);
        accountTccService.tryAction();
    }
}
