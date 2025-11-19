package com.transactioninsight.foundation.deadlock.order_inventory.model;

import lombok.Data;

/**
 * 套餐商品项（用于下单）
 */
@Data
public class PackageItem {
    private String productCode;  // 产品编码
    private Integer quantity;    // 购买数量
    
    public PackageItem(String productCode, Integer quantity) {
        this.productCode = productCode;
        this.quantity = quantity;
    }
}