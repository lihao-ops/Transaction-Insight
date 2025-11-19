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
 * 死锁场景复现测试
 *
 * 测试场景：
 * 100个用户并发购买"专业行情版套餐"（包含PRO_MONTH + LIMIT_UP_MONTH）
 *
 * 死锁原理：
 * 线程奇数：先锁PRO_MONTH(id=1) → 再锁LIMIT_UP_MONTH(id=10)
 * 线程偶数：先锁LIMIT_UP_MONTH(id=10) → 再锁PRO_MONTH(id=1)
 * → 形成循环等待 → MySQL检测死锁 → 回滚其中一个事务
 *
 * 预期结果：
 * ✅ 部分线程成功
 * ❌ 部分线程抛出 DeadlockLoserDataAccessException
 * 📊 成功率约50%-80%（取决于并发时序）
 */

@Slf4j
@SpringBootTest
public class DeadlockReproduceTest {

    @Resource
    private OrderService orderService;

    @Resource
    private ProductStockMapper stockMapper;

    /**
     * 每次测试前重置库存
     */
    @BeforeEach
    public void setup() {
        stockMapper.resetStock("PRO_MONTH", 1000);
        stockMapper.resetStock("LIMIT_UP_MONTH", 1000);
        log.info("库存已重置");
    }

    /**
     * 【必现死锁】模拟100用户并发购买套餐
     *
     * 运行前准备：
     * 1. 确保数据库已创建并初始化数据
     * 2. 调整日志级别查看详细执行过程
     *
     * 观察重点：
     * 1. 日志中的加锁顺序
     * 2. 异常堆栈中的 "Deadlock found"
     * 3. 最终成功/失败统计
     */
    @Test
    public void testDeadlockScenario() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        log.warn("========== 开始死锁复现测试 ==========");

        for (int i = 0; i < threadCount; i++) {
            final long userId = i;

            // 关键：奇偶用户按不同顺序传入商品（模拟真实随机场景）
            List<PackageItem> items = (userId % 2 == 0)
                    ? Arrays.asList(
                    new PackageItem("PRO_MONTH", 1),      // 偶数用户：先专业版
                    new PackageItem("LIMIT_UP_MONTH", 1)  // 后涨停选股
            )
                    : Arrays.asList(
                    new PackageItem("LIMIT_UP_MONTH", 1), // 奇数用户：先涨停选股
                    new PackageItem("PRO_MONTH", 1)       // 后专业版
            );

            executor.submit(() -> {
                try {
                    orderService.createOrderWithDeadlock(userId, items);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    if (e.getMessage().contains("Deadlock")) {
                        log.error("用户{}遭遇死锁：{}", userId, e.getMessage());
                    } else {
                        log.error("用户{}下单失败：{}", userId, e.getMessage());
                    }
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
        log.warn("失败: {}笔 (包含死锁)", failCount.get());
        log.warn("PRO_MONTH剩余库存: {}", stockMapper.getStock("PRO_MONTH"));
        log.warn("LIMIT_UP_MONTH剩余库存: {}", stockMapper.getStock("LIMIT_UP_MONTH"));

        // 断言：必定有死锁发生
        assert failCount.get() > 0 : "预期会发生死锁，但全部成功了！";
    }
}