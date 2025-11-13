package com.transactioninsight.foundation.mysql;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 类说明 / Class Description:
 * 中文：事务实验专用的账户交易实体，映射到 account_transaction 表，用于隔离级别、MVCC 与锁机制实验。
 * English: Account transaction entity for labs, mapped to account_transaction table, used in isolation, MVCC and lock experiments.
 *
 * 使用场景 / Use Cases:
 * 中文：在测试与演示中承载余额、版本号等核心字段，配合并发事务进行验证。
 * English: Carry core fields like balance and version in tests/demos, validated under concurrent transactions.
 *
 * 设计目的 / Design Purpose:
 * 中文：以最小字段集覆盖常见实验场景（余额、版本、更新时间），支持行锁与间隙锁分析。
 * English: Minimal field set to cover common lab scenarios (balance, version, updated time), supporting row/gap lock analysis.
 */
@Entity
@Table(name = "account_transaction")
public class AccountTransaction {

    /** 主键 ID（聚簇索引）。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户标识（用于二级索引演示）。 */
    @Column(name = "user_id")
    private Long userId;

    /** 账号编号（唯一索引实验可用）。 */
    @Column(name = "account_no", length = 64)
    private String accountNo;

    /** 账户类型。 */
    @Column(name = "account_type")
    private Integer accountType;

    /** 余额。 */
    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    /** 状态。 */
    @Column(name = "status")
    private Integer status;

    /** 风险等级。 */
    @Column(name = "risk_level")
    private Integer riskLevel;

    /** 分支机构。 */
    @Column(name = "branch_id")
    private Integer branchId;

    /** 最近交易时间。 */
    @Column(name = "last_trans_time")
    private LocalDateTime lastTransTime;

    /** 乐观锁版本号。 */
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    /** 冻结金额（配合锁实验）。 */
    @Column(name = "frozen_amount", nullable = false)
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    /** 更新时间。 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** JPA 默认构造函数。 */
    protected AccountTransaction() {}

    /**
     * 方法说明 / Method Description:
     * 中文：以必需字段构造实体，便于测试快速插入记录。
     * English: Construct entity with essential fields for quick test inserts.
     *
     * 参数 / Parameters:
     * @param balance 中文：余额 / English: Balance
     *
     * 返回值 / Return:
     * 中文：实体实例 / English: Entity instance
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    public AccountTransaction(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public Integer getVersion() { return version; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setVersion(Integer version) { this.version = version; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setFrozenAmount(BigDecimal frozenAmount) { this.frozenAmount = frozenAmount; }
}

