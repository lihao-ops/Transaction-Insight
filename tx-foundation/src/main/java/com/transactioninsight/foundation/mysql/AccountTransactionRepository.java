package com.transactioninsight.foundation.mysql;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 类说明 / Class Description:
 * 中文：账户交易仓储接口，提供基本 CRUD 能力，支撑事务与锁相关实验。
 * English: Account transaction repository interface providing CRUD, supporting transaction and lock experiments.
 *
 * 使用场景 / Use Cases:
 * 中文：在测试中快速插入/查询实验数据，配合并发场景验证。
 * English: Quickly insert/query lab data in tests, validating concurrent scenarios.
 *
 * 设计目的 / Design Purpose:
 * 中文：抽象数据访问层，简化测试代码并提升可读性。
 * English: Abstract data access to simplify test code and improve readability.
 */
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
}

