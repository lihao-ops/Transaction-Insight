package com.transactioninsight.foundation.deadlock.order_inventory.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 产品库存实体
 */
@Data
public class ProductStock {
    private Long id;
    private String productCode;      // 产品编码
    private String productName;      // 产品名称
    private String productType;      // 产品类型
    private String duration;         // 时长
    private Integer stockQuantity;   // 库存数量
    private Integer version;         // 乐观锁版本
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}