package com.transactioninsight.foundation.deadlock.order_inventory.service;


import com.transactioninsight.foundation.deadlock.order_inventory.mapper.ProductStockMapper;
import com.transactioninsight.foundation.deadlock.order_inventory.model.PackageItem;
import com.transactioninsight.foundation.deadlock.order_inventory.model.ProductStock;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    @Resource
    private ProductStockMapper stockMapper;

    /**
     * 【错误示范】不排序直接加锁 - 会导致死锁
     * 主键是递增聚簇索引 → MySQL 会按主键升序加锁 → 即使你不排序也不会死锁
     * <p>
     * 实验目的：
     * 模拟真实死锁场景，两个事务以不同顺序锁相同资源
     * <p>
     * 死锁原因：
     * 事务A：锁PRO_MONTH → 等待LIMIT_UP_MONTH
     * 事务B：锁LIMIT_UP_MONTH → 等待PRO_MONTH
     * <p>
     * 预期现象：
     * 并发执行时MySQL检测到死锁，抛出异常：
     * Deadlock found when trying to get lock; try restarting transaction
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrderWithDeadlock(Long userId, List<PackageItem> items) {
        log.info("[死锁测试] 用户{}开始下单，商品：{}", userId, items);

        // 1. 提取产品编码（不排序！）
        List<String> productCodes = items.stream()
                .map(PackageItem::getProductCode)
                .collect(Collectors.toList());

        // 2. 按传入顺序加锁（危险操作！）
        List<ProductStock> result = new ArrayList<>();
        for (String code : productCodes) {
            //todo 因主键id是递增索引，所以MySQL即便不排序也会加递增加锁，不会造成死锁，故此在此每次都随机查询一个任意其中一个code，不同线程加锁顺序不一致偶现死锁
            List<ProductStock> productStockList = stockMapper.selectByCodeForUpdateNoOrder(code);
            result.addAll(productStockList);
        }
        log.info("[死锁测试] 用户{}已锁定：{}", userId, productCodes);

        // 3. 模拟业务处理延迟（放大死锁概率）
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 校验并扣减库存
        Map<String, ProductStock> stockMap = result.stream()
                .collect(Collectors.toMap(ProductStock::getProductCode, s -> s));

        for (PackageItem item : items) {
            ProductStock stock = stockMap.get(item.getProductCode());
            if (stock.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("库存不足：" + stock.getProductName());
            }
            int updated = stockMapper.deductStock(item.getProductCode(), item.getQuantity());
            if (updated == 0) {
                throw new RuntimeException("扣减库存失败：" + stock.getProductName());
            }
        }

        log.info("[死锁测试] 用户{}订单完成", userId);
    }

    /**
     * 【正确实现】排序后加锁 - 避免死锁
     * <p>
     * 实验目的：
     * 通过统一加锁顺序打破循环等待条件
     * <p>
     * 核心原理：
     * 所有事务按 product_stock.id 升序加锁
     * → 事务A：等待id=5 → 获取id=10
     * → 事务B：等待id=5（排队）
     * → 不存在相互等待，无法形成死锁环
     * <p>
     * 预期现象：
     * 高并发下事务串行执行，不会死锁
     */
    @Transactional(rollbackFor = Exception.class)
    public void createOrderSafely(Long userId, List<PackageItem> items) {
        log.info("[安全下单] 用户{}开始下单，商品：{}", userId, items);

        // 1. 提取产品编码并排序（关键步骤！）
        // 注意：这里排序的是productCode，但SQL中ORDER BY id
        // MySQL会根据WHERE IN的结果按id排序加锁
        List<String> productCodes = items.stream()
                .map(PackageItem::getProductCode)
                .sorted()  // 先对业务参数排序保证一致性
                .collect(Collectors.toList());

        // 2. 按主键顺序加锁（SQL中的ORDER BY id生效）
        List<ProductStock> stocks = stockMapper.selectByCodesForUpdateOrdered(productCodes);
        log.info("[安全下单] 用户{}已按序锁定：{}", userId,
                stocks.stream().map(ProductStock::getId).collect(Collectors.toList()));

        // 3. 模拟业务处理延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 校验并扣减库存
        Map<String, ProductStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(ProductStock::getProductCode, s -> s));

        for (PackageItem item : items) {
            ProductStock stock = stockMap.get(item.getProductCode());
            if (stock.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("库存不足：" + stock.getProductName());
            }
            int updated = stockMapper.deductStock(item.getProductCode(), item.getQuantity());
            if (updated == 0) {
                throw new RuntimeException("扣减库存失败：" + stock.getProductName());
            }
        }

        log.info("[安全下单] 用户{}订单完成", userId);
    }
}