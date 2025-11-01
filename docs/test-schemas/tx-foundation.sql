-- tx-foundation 集成测试所需的表结构
-- 使用前请先创建数据库：CREATE DATABASE transaction_test CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0
);
