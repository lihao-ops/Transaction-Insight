package com.transactioninsight.distributed.tcc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AccountTccService implements TccAction {

    private static final Logger log = LoggerFactory.getLogger(AccountTccService.class);

    private final ConcurrentMap<Long, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, BigDecimal> frozen = new ConcurrentHashMap<>();

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

    public void freeze(Long accountId, BigDecimal amount) {
        balances.compute(accountId, (id, balance) -> balance.subtract(amount));
        frozen.merge(accountId, amount, BigDecimal::add);
        log.debug("Frozen {} for account {}", amount, accountId);
    }

    public void release(Long accountId, BigDecimal amount) {
        frozen.computeIfPresent(accountId, (id, frozenAmount) -> frozenAmount.subtract(amount));
        log.debug("Released {} from frozen for account {}", amount, accountId);
    }

    public void thaw(Long accountId) {
        BigDecimal amount = frozen.remove(accountId);
        if (amount != null) {
            balances.merge(accountId, amount, BigDecimal::add);
        }
    }

    public BigDecimal currentBalance(Long accountId) {
        return balances.get(accountId);
    }

    public BigDecimal frozenBalance(Long accountId) {
        return frozen.getOrDefault(accountId, BigDecimal.ZERO);
    }
}
