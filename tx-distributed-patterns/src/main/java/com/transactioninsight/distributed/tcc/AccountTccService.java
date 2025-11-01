package com.transactioninsight.distributed.tcc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存模拟版账户服务，实现 TCC 三个阶段以便在单元测试中观察余额变化。
 */
@Service
public class AccountTccService implements TccAction {

    private static final Logger log = LoggerFactory.getLogger(AccountTccService.class);

    private final ConcurrentMap<Long, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, BigDecimal> frozen = new ConcurrentHashMap<>();

    /**
     * 初始化两个账号的余额，模拟数据库状态。
     */
    public AccountTccService() {
        balances.put(1L, new BigDecimal("1000"));
        balances.put(2L, new BigDecimal("500"));
    }

    @Override
    public void tryAction() {
        freeze(1L, new BigDecimal("100"));
    }

    @Override
    public void confirm() {
        release(1L, new BigDecimal("100"));
    }

    @Override
    public void cancel() {
        thaw(1L);
    }

    /**
     * Try 阶段：冻结余额，同时扣减可用余额。
     */
    public void freeze(Long accountId, BigDecimal amount) {
        balances.compute(accountId, (id, balance) -> balance.subtract(amount));
        frozen.merge(accountId, amount, BigDecimal::add);
        log.debug("Frozen {} for account {}", amount, accountId);
    }

    /**
     * Confirm 阶段：释放冻结金额（表示资金已结算）。
     */
    public void release(Long accountId, BigDecimal amount) {
        frozen.computeIfPresent(accountId, (id, frozenAmount) -> frozenAmount.subtract(amount));
        log.debug("Released {} from frozen for account {}", amount, accountId);
    }

    /**
     * Cancel 阶段：归还冻结金额，恢复可用余额。
     */
    public void thaw(Long accountId) {
        BigDecimal amount = frozen.remove(accountId);
        if (amount != null) {
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
