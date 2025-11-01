package com.transactioninsight.foundation.mysql;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 基于 Spring Data JPA 的账户仓储接口，统一封装 CRUD 能力，供多模块复用。
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
}
