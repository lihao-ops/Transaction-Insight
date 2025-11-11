package com.transactioninsight.chaos;

import com.transactioninsight.distributed.DistributedPatternsApplication;
import com.transactioninsight.distributed.tcc.AccountTccService;
import com.transactioninsight.distributed.tcc.TccTransactionManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 测试目标：为 TCC 分布式事务注入网络/数据库故障的混沌实验模板。
 * 事务知识点：Try-Confirm-Cancel 的补偿与幂等设计，重点观察 Confirm 失败时的回滚策略。
 * 说明：测试默认禁用，需配合人工或外部工具制造故障，运行后可验证 TCC 补偿链路是否生效。
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
