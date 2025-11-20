package com.transactioninsight.foundation.deadlock.order_inventory.service;


import com.transactioninsight.foundation.deadlock.order_inventory.mapper.ProductStockMapper;
import com.transactioninsight.foundation.deadlock.order_inventory.model.PackageItem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 顺序加锁解决死锁测试
 * <p>
 * 测试目标：
 * 验证ORDER BY方案在高并发下100%无死锁
 * <p>
 * 核心机制：
 * 无论业务层传入顺序如何，SQL层统一按 id ASC 加锁
 * → 所有事务排队等待，不会形成循环依赖
 * <p>
 * 预期结果：
 * ✅ 100%成功率（无死锁）
 * ✅ 库存扣减准确
 * ✅ 性能损耗可控（仅增加排序开销）
 */
@Slf4j
@SpringBootTest
public class DeadlockSolutionTest {

    @Resource
    private OrderService orderService;

    @Resource
    private ProductStockMapper stockMapper;

    @BeforeEach
    public void setup() {
        stockMapper.resetStock("PRO_MONTH", 1000);
        stockMapper.resetStock("LIMIT_UP_MONTH", 1000);
        log.info("库存已重置");
    }

    /**
     * 【零死锁】100用户并发购买 - 顺序加锁方案
     * <p>
     * 对比实验：
     * 与 DeadlockReproduceTest 使用相同并发量和场景
     * 唯一区别：使用 createOrderSafely 方法
     * <p>
     * 验证点：
     * 1. 无 Deadlock 异常
     * 2. 100笔订单全部成功
     * 3. 库存精确扣减200（每人买2件）
     */
    @Test
    public void testNoDeadlockWithOrdering() throws InterruptedException {
        /**
         * ✔ 你的顺序加锁方案 100% 有效，确实完全避免了死锁
         * ❌ 失败并不是逻辑问题，而是连接池资源不足
         * ❌ 并发 1000 + 每个事务 Sleep(100ms) + 线程池 50 + 连接池 10
         * → 任何系统都会被打爆，没有例外
         * 你现在只需要：
         * 去掉 sleep 或缩短到 5ms
         * 调大连接池
         * 降低线程池并发
         * 或减少一次性并发数量
         * 你这套顺序加锁代码是正确的，完全没有问题。
         */
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        log.warn("========== 开始顺序加锁测试 ==========");

        for (int i = 0; i < threadCount; i++) {
            final long userId = i;

            // 同样随机顺序传入（但SQL层会统一排序）
            List<PackageItem> items = (userId % 2 == 0)
                    ? Arrays.asList(
                    new PackageItem("PRO_MONTH", 1),
                    new PackageItem("LIMIT_UP_MONTH", 1)
            )
                    : Arrays.asList(
                    new PackageItem("LIMIT_UP_MONTH", 1),
                    new PackageItem("PRO_MONTH", 1)
            );

            executor.submit(() -> {
                try {
                    orderService.createOrderSafely(userId, items);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("用户{}下单异常：{}", userId, e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证结果
        log.warn("========== 测试结果 ==========");
        log.warn("成功: {}笔", successCount.get());
        log.warn("失败: {}笔", failCount.get());
        log.warn("PRO_MONTH剩余库存: {}", stockMapper.getStock("PRO_MONTH"));
        log.warn("LIMIT_UP_MONTH剩余库存: {}", stockMapper.getStock("LIMIT_UP_MONTH"));

        // 严格断言
        assert failCount.get() == 0 : "不应有任何失败！";
        assert successCount.get() == threadCount : "必须全部成功！";
        assert stockMapper.getStock("PRO_MONTH") == 900 : "库存扣减错误！";
        assert stockMapper.getStock("LIMIT_UP_MONTH") == 900 : "库存扣减错误！";

        log.warn("✅ 测试通过：零死锁，库存准确！");
    }
}