package com.transactioninsight.foundation.mysql;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 类说明 / Class Description:
 * 中文：账户仓储接口，基于 Spring Data JPA 提供标准化的 CRUD 能力。
 * English: Account repository interface providing standardized CRUD powered by Spring Data JPA.
 *
 * 使用场景 / Use Cases:
 * 中文：在事务实验、并发示例与集成测试中统一访问账户数据。
 * English: Unified account data access in transaction labs, concurrency demos, and integration tests.
 *
 * 设计目的 / Design Purpose:
 * 中文：抽象数据访问层，减少样例重复，实现模块间可复用的仓储契约。
 * English: Abstract data access to reduce duplication and provide reusable repository contract across modules.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
}
