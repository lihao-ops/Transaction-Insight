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

@Service
public class MvccVisualizer {

    private static final Logger log = LoggerFactory.getLogger(MvccVisualizer.class);

    private final AccountRepository accountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public MvccVisualizer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public SnapshotResult demonstrateMvcc(Long accountId) {
        Account baseline = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        log.debug("Read view captured for account {} with balance {}", accountId, baseline.getBalance());

        entityManager.flush();
        entityManager.clear();

        Account snapshot = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        return new SnapshotResult(snapshot.getBalance(), List.of("Initial ReadView captured"));
    }

    public record SnapshotResult(BigDecimal balance, List<String> events) {
    }
}
