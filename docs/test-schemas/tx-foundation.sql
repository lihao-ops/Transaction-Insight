-- tx-foundation 集成测试所需的表结构
-- 使用前请先创建数据库：CREATE DATABASE transaction_test CHARACTER SET utf8mb4;
-- 该表用于模拟账户体系，验证余额冻结、扣减等资金操作的业务流程。
CREATE TABLE IF NOT EXISTS account (
    -- 自增主键，唯一标识账户
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 账户当前可用余额
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    -- 已冻结但暂未解冻的金额，用于模拟事务占用资金
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0
);
