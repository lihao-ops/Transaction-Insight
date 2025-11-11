package com.transactioninsight.springcore.mysql.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(name = "accounts")
public class Account {

    private static final int SCALE = 2;

    @Id
    private Long id;

    @Column(name = "owner_name", nullable = false, length = 64)
    private String ownerName;

    @Column(name = "balance", nullable = false, precision = 19, scale = SCALE)
    private BigDecimal balance;

    @Version
    private Long version;

    protected Account() {
        // for JPA
    }

    public Account(Long id, String ownerName, BigDecimal balance) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName must not be null");
        this.balance = normalize(Objects.requireNonNull(balance, "balance must not be null"));
    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(validateAmount(amount));
    }

    public void debit(BigDecimal amount) {
        BigDecimal debit = validateAmount(amount);
        if (balance.compareTo(debit) < 0) {
            throw new IllegalArgumentException("Insufficient balance for account " + id);
        }
        balance = balance.subtract(debit);
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return normalize(amount);
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
