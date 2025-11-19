package com.transactioninsight.foundation.deadlock.order_inventory.mapper;

import com.transactioninsight.foundation.deadlock.order_inventory.model.ProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 产品库存 Mapper
 * 注意：所有 SQL 已迁移到 XML 配置文件
 */
@Mapper
public interface ProductStockMapper {

    /**
     * 【会导致死锁】无序加锁查询库存
     * 实验目的：模拟死锁场景，按传入顺序加锁
     * 预期现象：并发时出现 Deadlock found when trying to get lock
     */
    List<ProductStock> selectByCodeForUpdateNoOrder(@Param("code") String code);

    /**
     * 【解决死锁】有序加锁查询库存
     * 实验目的：通过统一加锁顺序避免死锁
     * 预期现象：并发时按主键升序排队，不会死锁
     * 核心原理：ORDER BY id 强制MySQL按主键顺序加行锁
     */
    List<ProductStock> selectByCodesForUpdateOrdered(@Param("codes") List<String> codes);

    /**
     * 扣减库存（带库存校验）
     * 注意：此方法必须在已加行锁后调用
     *
     * @return 影响行数（0表示库存不足或商品不存在）
     */
    int deductStock(@Param("code") String productCode, @Param("quantity") Integer quantity);

    /**
     * 查询当前库存（不加锁，用于测试验证）
     *
     * @return 库存数量，null表示商品不存在
     */
    Integer getStock(@Param("code") String productCode);

    /**
     * 重置库存（测试用）
     */
    void resetStock(@Param("code") String productCode, @Param("quantity") Integer quantity);
}