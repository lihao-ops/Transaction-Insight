package com.transactioninsight.foundation.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * <p>
 * JPA 映射的账户实体，对应测试数据库中的 {@code account} 表。该实体既服务于 MVCC 可视化、
 * 也用于死锁复现示例，是整个项目中最基础的数据模型之一。
 * </p>
 */
@Entity
@Table(name = "account")
public class Account {

    /** 主键，自增策略用于模拟真实生产库的流水号。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 当前可用余额。 */
    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * 冻结金额，用于模拟分布式事务预留资金场景。与 {@link #balance} 一起帮助演示 TCC 或 MVCC。
     */
    @Column(name = "frozen_balance", nullable = false)
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    /**
     * JPA 默认构造函数，保留给框架使用。
     */
    public Account() {
    }

    /**
     * 初始化账户余额，常用于测试场景中快速构造账户。
     *
     * @param balance 初始可用余额
     */
    public Account(BigDecimal balance) {
        this.balance = balance;
        this.frozenBalance = BigDecimal.ZERO;
    }

    /**
     * @return 数据库生成的主键
     */
    public Long getId() {
        return id;
    }

    /**
     * @return 当前可用余额
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * 更新可用余额。
     *
     * @param balance 新的余额数值
     */
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    /**
     * @return 当前冻结金额
     */
    public BigDecimal getFrozenBalance() {
        return frozenBalance;
    }

    /**
     * 更新冻结金额，通常由事务模拟代码调用。
     *
     * @param frozenBalance 要写入的冻结金额
     */
    public void setFrozenBalance(BigDecimal frozenBalance) {
        this.frozenBalance = frozenBalance;
    }
}
