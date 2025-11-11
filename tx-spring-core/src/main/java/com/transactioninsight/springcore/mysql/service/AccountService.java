package com.transactioninsight.springcore.mysql.service;

import com.transactioninsight.springcore.mysql.domain.Account;
import com.transactioninsight.springcore.mysql.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void resetAccounts(List<AccountSeed> seeds) {
        accountRepository.deleteAllInBatch();
        accountRepository.flush();
        List<Account> accounts = seeds.stream()
                .map(seed -> new Account(seed.id(), seed.ownerName(), seed.balance()))
                .toList();
        accountRepository.saveAll(accounts);
    }

    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        if (Objects.equals(fromId, toId)) {
            throw new IllegalArgumentException("Cannot transfer within the same account");
        }
        Account source = accountRepository.findForUpdate(fromId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + fromId));
        Account target = accountRepository.findForUpdate(toId)
                .orElseThrow(() -> new IllegalArgumentException("Target account not found: " + toId));
        source.debit(amount);
        target.credit(amount);
    }

    @Transactional
    public void transferAndFail(Long fromId, Long toId, BigDecimal amount) {
        transfer(fromId, toId, amount);
        throw new IllegalStateException("Simulated failure after transfer");
    }

    @Transactional
    public void deposit(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.credit(amount);
    }

    @Transactional
    public void withdraw(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.debit(amount);
    }

    public BigDecimal getBalance(Long accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getBalance)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    public BigDecimal totalBalance() {
        return accountRepository.sumBalances();
    }

    public record AccountSeed(Long id, String ownerName, BigDecimal balance) {
        public AccountSeed {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(ownerName, "ownerName must not be null");
            Objects.requireNonNull(balance, "balance must not be null");
        }
    }
}
