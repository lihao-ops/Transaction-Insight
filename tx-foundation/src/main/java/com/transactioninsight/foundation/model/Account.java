package com.transactioninsight.foundation.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "account_lock")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String accountNo;

    @Column(nullable = false)
    private BigDecimal balance;

    @Version
    private Long version; // 乐观锁版本号
}