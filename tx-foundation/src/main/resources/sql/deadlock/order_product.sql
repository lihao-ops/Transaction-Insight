USE `transaction_study`;

-- 产品库存表
CREATE TABLE `product_stock` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `product_code` VARCHAR(50) NOT NULL UNIQUE COMMENT '产品编码（如：PRO_MONTH）',
  `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称（如：专业行情版-月）',
  `product_type` VARCHAR(20) NOT NULL COMMENT '产品类型（PROFESSIONAL/DEPTH/SUPER/LIMIT_UP/REVERSAL）',
  `duration` VARCHAR(10) NOT NULL COMMENT '时长（MONTH/QUARTER/YEAR）',
  `stock_quantity` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_product_type` (`product_type`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='产品库存表';

-- 初始化测试数据
INSERT INTO `product_stock` (`product_code`, `product_name`, `product_type`, `duration`, `stock_quantity`) VALUES
-- 专业行情版
('PRO_MONTH', '专业行情版-月', 'PROFESSIONAL', 'MONTH', 1000),
('PRO_QUARTER', '专业行情版-季', 'PROFESSIONAL', 'QUARTER', 1000),
('PRO_YEAR', '专业行情版-年', 'PROFESSIONAL', 'YEAR', 1000),
-- 深度资料版
('DEPTH_MONTH', '深度资料版-月', 'DEPTH', 'MONTH', 1000),
('DEPTH_QUARTER', '深度资料版-季', 'DEPTH', 'QUARTER', 1000),
('DEPTH_YEAR', '深度资料版-年', 'DEPTH', 'YEAR', 1000),
-- 超级机构版
('SUPER_MONTH', '超级机构版-月', 'SUPER', 'MONTH', 1000),
('SUPER_QUARTER', '超级机构版-季', 'SUPER', 'QUARTER', 1000),
('SUPER_YEAR', '超级机构版-年', 'SUPER', 'YEAR', 1000),
-- 涨停选股
('LIMIT_UP_MONTH', '涨停选股-月', 'LIMIT_UP', 'MONTH', 1000),
('LIMIT_UP_QUARTER', '涨停选股-季', 'LIMIT_UP', 'QUARTER', 1000),
('LIMIT_UP_YEAR', '涨停选股-年', 'LIMIT_UP', 'YEAR', 1000),
-- 反转信号
('REVERSAL_MONTH', '反转信号-月', 'REVERSAL', 'MONTH', 1000),
('REVERSAL_QUARTER', '反转信号-季', 'REVERSAL', 'QUARTER', 1000),
('REVERSAL_YEAR', '反转信号-年', 'REVERSAL', 'YEAR', 1000);

-- 订单表（简化）
CREATE TABLE `product_order` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `status` VARCHAR(20) NOT NULL COMMENT '订单状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
CREATE TABLE `order_item` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '明细ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `product_code` VARCHAR(50) NOT NULL COMMENT '产品编码',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
  INDEX `idx_order_id` (`order_id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';