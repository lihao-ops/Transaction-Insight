package com.transactioninsight.foundation.mvcc;

import com.transactioninsight.foundation.mysql.Account;
import com.transactioninsight.foundation.mysql.AccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * MVCC 可视化服务：在 REPEATABLE_READ 隔离级别下，演示同一个事务对同一条记录的两次读取
 * 返回一致快照的行为。该服务是基础模块中最核心的教学示例之一。
 * </p>
 */
@Service
public class MvccVisualizer {

    private static final Logger log = LoggerFactory.getLogger(MvccVisualizer.class);

    private final AccountRepository accountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @param accountRepository JPA 仓储，用于加载账户快照
     */
    public MvccVisualizer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 在可重复读隔离级别下查询账户，并返回事务内视图。
     *
     * @param accountId 需要演示的账户 ID
     * @return 快照结果，包含可见余额与可视化事件
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public SnapshotResult demonstrateMvcc(Long accountId) {
        Account baseline = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        log.debug("Read view captured for account {} with balance {}", accountId, baseline.getBalance());

        // 核心步骤：通过 flush + clear 强制 JPA 再次从数据库加载数据，以此对比事务视图与最新数据。
        entityManager.flush();
        entityManager.clear();

        Account snapshot = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return new SnapshotResult(snapshot.getBalance(), List.of("Initial ReadView captured"));
    }

    /**
     * 记录快照信息的简单值对象。
     *
     * @param balance 此次演示得到的余额
     * @param events  快照过程中的关键事件描述
     */
    public record SnapshotResult(BigDecimal balance, List<String> events) {
    }
}
