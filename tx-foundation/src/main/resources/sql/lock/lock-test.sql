SELECT @@transaction_isolation;

-- 检查InnoDB引擎
SHOW ENGINES;

USE `transaction_study`;


CREATE TABLE account_lock (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户的唯一标识符',
                              account_no VARCHAR(50) NOT NULL UNIQUE COMMENT '账户编号，必须唯一',
                              balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额，精度为 15 位数字，其中小数部分为 2 位',
                              VERSION BIGINT DEFAULT 0 COMMENT '乐观锁版本号，用于实现乐观锁机制',
                              INDEX idx_account_no (account_no) COMMENT '根据账户编号建立索引，提高查询效率'
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='账户表lock，用于存储账户信息，包括账户编号、余额和版本号';

-- 插入测试数据
INSERT INTO account (account_no, balance, VERSION) VALUES
                                                       ('ACC001', 1000.00, 0),
                                                       ('ACC002', 2000.00, 0);
