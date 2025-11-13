package com.transactioninsight.foundation.mysql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 类说明 / Class Description:
 * 中文：JPA 账户实体，对应测试数据库的 account 表，作为事务实验的基础数据模型。
 * English: JPA Account entity mapped to the account table, foundational data model for transaction labs.
 *
 * 使用场景 / Use Cases:
 * 中文：在 MVCC 可视化、死锁复现、TCC 预留资金等场景下承载账户余额状态。
 * English: Used in MVCC visualization, deadlock reproduction, and TCC reserved funds scenarios.
 *
 * 设计目的 / Design Purpose:
 * 中文：以最小字段集表达事务状态变化，支持读取一致性与并发更新的演示。
 * English: Minimal field set to express transactional state changes, supporting consistency and concurrent updates demos.
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
     * 方法说明 / Method Description:
     * 中文：无参构造，供 JPA 框架反射实例化实体。
     * English: No-arg constructor for JPA framework reflective instantiation.
     *
     * 参数 / Parameters:
     * 无
     *
     * 返回值 / Return:
     * 中文说明：无
     * English description: None
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    public Account() {
    }

    /**
     * 方法说明 / Method Description:
     * 中文：以初始余额构造实体，用于测试与演示快速建模。
     * English: Construct entity with initial balance for quick modeling in tests and demos.
     *
     * 参数 / Parameters:
     * @param balance 中文说明：初始可用余额
     *                English description: Initial available balance
     *
     * 返回值 / Return:
     * 中文说明：实体实例
     * English description: Entity instance
     *
     * 异常 / Exceptions:
     * 中文/英文：无
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
