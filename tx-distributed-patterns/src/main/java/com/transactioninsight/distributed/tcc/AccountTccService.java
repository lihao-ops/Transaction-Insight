package com.transactioninsight.distributed.tcc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 类说明 / Class Description:
 * 中文：内存模拟账户服务，实现 TCC 三个阶段（Try/Confirm/Cancel）以观察余额变化。
 * English: In-memory account service implementing TCC phases (Try/Confirm/Cancel) to observe balance changes.
 *
 * 使用场景 / Use Cases:
 * 中文：在单元测试与演示环境中复现 TCC 资金预留、确认与回滚逻辑。
 * English: Reproduce TCC reserved funds, confirmation and rollback logic in tests and demos.
 *
 * 设计目的 / Design Purpose:
 * 中文：用最简单的数据结构表达 TCC 行为，避免外部依赖并提升可读性。
 * English: Express TCC behavior with minimal data structures, avoiding external deps and improving readability.
 */
@Service
public class AccountTccService implements TccAction {

    private static final Logger log = LoggerFactory.getLogger(AccountTccService.class);

    private final ConcurrentMap<Long, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, BigDecimal> frozen = new ConcurrentHashMap<>();

    /**
     * 方法说明 / Method Description:
     * 中文：初始化两个账号的余额，模拟数据库状态。
     * English: Initialize balances for two accounts to simulate DB state.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    public AccountTccService() {
        // 中文：初始化账户 1 与 2 的可用余额
        // English: Initialize available balances for accounts 1 and 2
        balances.put(1L, new BigDecimal("1000"));
        balances.put(2L, new BigDecimal("500"));
    }

    @Override
    /**
     * 方法说明 / Method Description:
     * 中文：TCC Try 阶段，执行冻结示例。
     * English: TCC Try phase executing freeze example.
     */
    public void tryAction() {
        freeze(1L, new BigDecimal("100"));
    }

    @Override
    /**
     * 方法说明 / Method Description:
     * 中文：TCC Confirm 阶段，释放冻结金额。
     * English: TCC Confirm phase releasing frozen amount.
     */
    public void confirm() {
        release(1L, new BigDecimal("100"));
    }

    @Override
    /**
     * 方法说明 / Method Description:
     * 中文：TCC Cancel 阶段，取消冻结并归还余额。
     * English: TCC Cancel phase canceling freeze and returning balance.
     */
    public void cancel() {
        thaw(1L);
    }

    /**
     * 方法说明 / Method Description:
     * 中文：Try 阶段冻结余额并扣减可用余额。
     * English: Freeze balance and deduct available balance in Try phase.
     *
     * 参数 / Parameters:
     * @param accountId 中文：账户 ID / English: Account ID
     * @param amount    中文：冻结金额 / English: Amount to freeze
     *
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    public void freeze(Long accountId, BigDecimal amount) {
        // 中文：扣减可用余额
        // English: Deduct available balance
        balances.compute(accountId, (id, balance) -> balance.subtract(amount));
        // 中文：增加冻结余额
        // English: Increase frozen balance
        frozen.merge(accountId, amount, BigDecimal::add);
        log.debug("Frozen {} for account {}", amount, accountId);
    }

    /**
     * 方法说明 / Method Description:
     * 中文：Confirm 阶段释放冻结金额（资金已结算）。
     * English: Release frozen amount in Confirm phase (funds settled).
     *
     * 参数 / Parameters:
     * @param accountId 中文：账户 ID / English: Account ID
     * @param amount    中文：释放金额 / English: Amount to release
     *
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    public void release(Long accountId, BigDecimal amount) {
        // 中文：减少冻结余额
        // English: Reduce frozen balance
        frozen.computeIfPresent(accountId, (id, frozenAmount) -> frozenAmount.subtract(amount));
        log.debug("Released {} from frozen for account {}", amount, accountId);
    }

    /**
     * 方法说明 / Method Description:
     * 中文：Cancel 阶段归还冻结金额，并恢复可用余额。
     * English: Return frozen amount and restore available balance in Cancel phase.
     *
     * 参数 / Parameters:
     * @param accountId 中文：账户 ID / English: Account ID
     *
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    public void thaw(Long accountId) {
        // 中文：移除冻结余额并取出金额
        // English: Remove frozen balance and fetch amount
        BigDecimal amount = frozen.remove(accountId);
        if (amount != null) {
            // 中文：归还到可用余额
            // English: Return to available balance
            balances.merge(accountId, amount, BigDecimal::add);
        }
    }

    /**
     * @return 指定账户的当前可用余额
     */
    public BigDecimal currentBalance(Long accountId) {
        return balances.get(accountId);
    }

    /**
     * @return 指定账户的冻结余额
     */
    public BigDecimal frozenBalance(Long accountId) {
        return frozen.getOrDefault(accountId, BigDecimal.ZERO);
    }
}
