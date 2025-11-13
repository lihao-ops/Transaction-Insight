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
 * 类说明 / Class Description:
 * 中文：MVCC 可视化服务，在可重复读隔离级别下演示同一事务的快照一致性行为。
 * English: MVCC visualization service demonstrating snapshot consistency under REPEATABLE_READ.
 *
 * 使用场景 / Use Cases:
 * 中文：教学展示事务内两次读取的视图一致性，对比 flush/clear 后与数据库最新值。
 * English: Educational demo for in-transaction read view consistency versus latest DB values after flush/clear.
 *
 * 设计目的 / Design Purpose:
 * 中文：以最小示例揭示 MVCC 的核心机制（快照、可见性）。
 * English: Reveal MVCC core mechanisms (snapshot, visibility) via minimal examples.
 */
@Service
public class MvccVisualizer {

    private static final Logger log = LoggerFactory.getLogger(MvccVisualizer.class);

    private final AccountRepository accountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 方法说明 / Method Description:
     * 中文：构造可视化服务并注入账户仓储。
     * English: Construct visualization service with injected account repository.
     *
     * 参数 / Parameters:
     * @param accountRepository 中文说明：用于查询账户视图的仓储
     *                         English description: Repository used to query account views
     *
     * 返回值 / Return:
     * 中文说明：服务实例
     * English description: Service instance
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    public MvccVisualizer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 方法说明 / Method Description:
     * 中文：在 REPEATABLE_READ 下查询账户两次，返回事务内快照视图。
     * English: Query the account twice under REPEATABLE_READ and return the in-transaction snapshot view.
     *
     * 参数 / Parameters:
     * @param accountId 中文说明：待演示的账户 ID
     *                  English description: Account ID for demonstration
     *
     * 返回值 / Return:
     * 中文说明：包含余额与事件的快照结果
     * English description: Snapshot result containing balance and events
     *
     * 异常 / Exceptions:
     * 中文/英文：账户不存在时抛 IllegalArgumentException
     *
     * 逻辑概述 / Logic Overview:
     * 中文：先读取基线视图，flush/clear 强制下一次读取走数据库，再比较事务视图与最新数据。
     * English: Read baseline view, then flush/clear to force next read from DB, comparing view with latest data.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public SnapshotResult demonstrateMvcc(Long accountId) {
        // 中文：加载事务内第一次读取的账户视图
        // English: Load the first in-transaction account read view
        Account baseline = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        // 中文：记录可视化日志以便教学演示
        // English: Log visualization details for educational demo
        log.debug("Read view captured for account {} with balance {}", accountId, baseline.getBalance());

        // 中文：通过 flush + clear 强制下一次读取命中数据库而非一级缓存
        // English: Force next read to hit the database instead of first-level cache via flush + clear
        entityManager.flush();
        entityManager.clear();

        // 中文：第二次读取，用于对比快照一致性
        // English: Second read for snapshot consistency comparison
        Account snapshot = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        // 中文：返回快照结果与事件说明
        // English: Return snapshot result with event annotation
        return new SnapshotResult(snapshot.getBalance(), List.of("Initial ReadView captured"));
    }

    /**
     * 类说明 / Class Description:
     * 中文：快照结果值对象，记录余额与事件说明。
     * English: Snapshot result value object recording balance and event annotations.
     *
     * 使用场景 / Use Cases:
     * 中文：在 MVCC 演示中向调用方返回事务内视图信息。
     * English: Return in-transaction view details to callers during MVCC demonstration.
     *
     * 设计目的 / Design Purpose:
     * 中文：用最简单的数据结构承载可视化信息，便于日志与断言。
     * English: Minimal data structure to carry visualization info for logging and assertions.
     */
    public record SnapshotResult(BigDecimal balance, List<String> events) {
    }
}
