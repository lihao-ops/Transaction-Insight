package com.transactioninsight.foundation.model;


import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // 普通查询 - 不加锁
    Optional<Account> findByAccountNo(String accountNo);

    // 悲观读锁 - 共享锁(S锁)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findByAccountNoWithSharedLock(@Param("accountNo") String accountNo);

    // 悲观写锁 - 排他锁(X锁)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findByAccountNoWithExclusiveLock(@Param("accountNo") String accountNo);
}