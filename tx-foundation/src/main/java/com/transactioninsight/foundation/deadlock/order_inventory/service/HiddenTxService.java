package com.transactioninsight.foundation.deadlock.order_inventory.service;

import com.transactioninsight.foundation.deadlock.order_inventory.mapper.ProductStockMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;


@Slf4j
@Service
public class HiddenTxService {

    @Resource
    private ProductStockMapper stockMapper;

    @Resource
    private PlatformTransactionManager txManager;

    /**
     * 无法复现一直未提交事务
     * Spring 在这里开启事务，但我们故意吃掉异常 → 不 commit、不 rollback。
     * todo 但是你以为异常被 catch，事务没有提交，但 Spring 的事务拦截器会在方法退出时自动回滚。你无法“卡住一个事务”，除非你故意破坏 Spring AOP 的事务流程。
     * 会造成：
     * 1. 方法退出了
     * 2. Spring AOP 的事务拦截器会接管事务
     * 3. 事务被自动结束：rollback
     * 4. 连接被归还到连接池
     * 5. 所有锁都释放了
     * 6. 因此 innodb_trx 看不到任何挂起事务
     * <p>
     * ✔ @Transactional 的事务范围是 方法级生命周期
     * 方法执行结束 → 事务必须结束（无论提交还是回滚）
     * 你没有手动 commit → Spring 自动 rollback。
     * <p>
     * 所以你永远看不到挂着的事务。
     */
    @Transactional
    public void doUpdateWithHiddenTransaction() {
        try {
            log.warn("[模拟长事务] 开始执行更新...");
            stockMapper.deductStock("PRO_MONTH", 1);

            // 故意抛个异常
            int x = 1 / 0;

        } catch (Exception e) {
            log.error("[模拟长事务] 异常已被捕获（未触发rollback）");
            // 注意：这里没有重新抛异常 → 当前事务保持“未提交”状态
        }

        log.warn("[模拟长事务] 方法结束，但事务仍然处于未提交状态（因为异常被吞掉）");
    }

    /**
     * 真正会造成长事务：手动开启事务、手动不提交
     *
     * 复现未提交事务（长事务）的核心步骤：
     * 1. 使用编程式事务手动开启事务（绕过 @Transactional 的方法级生命周期）。
     * 2. 执行更新语句，InnoDB 会对目标行加 X 锁，但不会释放。
     * 3. 故意制造异常并 catch，使 Spring 无法自动 rollback。
     * 4. 整个方法不调用 commit/rollback，事务保持 RUNNING 状态。
     * 5. 只要线程/连接不结束，这个事务就一直存在（可在 innodb_trx 查询到）。
     */
    public void openLongTxWithoutCommit() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        // 手动开启事务（不会自动关闭）
        TransactionStatus status = txManager.getTransaction(def);

        try {
            log.warn("[长事务] 执行更新...");
            stockMapper.deductStock("PRO_MONTH", 1);

            int x = 1 / 0; // 故意异常

        } catch (Exception e) {
            log.error("[长事务] 异常已捕获，但我们不提交、不回滚！事务挂住！");
            // ❗ 不 rollback、不中止、不中断
        }
        // ❗ 方法退出 → 事务依然打开没有提交
        log.warn("[长事务] 方法退出，但事务仍然保持打开状态（不会 auto rollback）");
    }
}
