package com.transactioninsight.foundation.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类说明 / Class Description:
 * 中文：间隙锁与 Next-Key Lock 实验，验证唯一索引等值存在/不存在、非唯一索引范围查询的加锁行为。
 * English: Gap and Next-Key Lock experiments verifying locking on unique equality exist/non-exist and non-unique range queries.
 *
 * 使用场景 / Use Cases:
 * 中文：理解快照读与当前读的差异，掌握插入阻塞的触发条件与范围。
 * English: Understand differences between snapshot vs current reads, mastering insert blocking conditions and ranges.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过短超时和双会话构造稳定的阻塞与超时路径，便于重复验证。
 * English: Use short timeouts and two sessions to stably construct blocking and timeout paths for reproducibility.
 */
@SpringBootTest
public class GapAndNextKeyLockTest {

    @Autowired
    private DataSource dataSource;

    private void seedBalances() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "user_id BIGINT, account_no VARCHAR(64), account_type INT, " +
                            "balance DECIMAL(15,2) NOT NULL, status INT, risk_level INT, branch_id INT, last_trans_time DATETIME, " +
                            "version INT NOT NULL DEFAULT 0, frozen_amount DECIMAL(15,2) NOT NULL DEFAULT 0, updated_at DATETIME NULL) ENGINE=InnoDB");
            c.createStatement().executeUpdate("CREATE UNIQUE INDEX idx_account_no ON account_transaction(account_no)");
            c.createStatement().executeUpdate("CREATE INDEX idx_balance ON account_transaction(balance)");
            c.createStatement().executeUpdate("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES " +
                    "(1,'ACC20240001',1,15000,1,0,101,NOW())," +
                    "(2,'ACCX0001',1,0,1,0,101,NOW())," +
                    "(3,'ACCX0002',1,500,1,0,101,NOW())," +
                    "(4,'ACCX0003',1,5000,1,0,101,NOW())," +
                    "(5,'ACCX0004',1,6500,1,0,101,NOW())," +
                    "(6,'ACCX0005',1,7200,1,0,101,NOW())," +
                    "(7,'ACCX0006',1,20000,1,0,101,NOW())," +
                    "(8,'ACCX0007',1,100000,1,0,101,NOW())");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：唯一索引等值存在 → Record Lock，无 Gap；不存在 → Gap Lock 至正无穷，阻止插入。
     * English: Unique equality existing → Record Lock, no Gap; non-existing → Gap Lock to +∞, blocking insert.
     */
    @Test
    @DisplayName("Lock-7A: Unique equality exist vs non-exist (Record vs Gap Lock)")
    void uniqueEqualityExistVsNonExist() throws Exception {
        seedBalances();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            b.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 2");

            // 中文：记录存在 → Record Lock
            // English: Existing record → Record Lock
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE account_no = 'ACC20240001' FOR UPDATE")) { ps.executeQuery(); }

            // 中文：插入相邻记录成功（不受 Gap 影响）
            // English: Insert adjacent record succeeds (no Gap effect)
            try (PreparedStatement ps = b.prepareStatement("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES (3001,'ACC20240000',1,1000,1,0,101,NOW())")) { ps.executeUpdate(); }
            b.commit();

            a.rollback();

            // 中文：记录不存在 → Gap Lock（会话A）
            // English: Non-existent record → Gap Lock (Session A)
            a.setAutoCommit(false);
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE account_no = 'ACC20249999' FOR UPDATE")) { ps.executeQuery(); }

            // 中文：会话B尝试在间隙插入，预期超时1205
            // English: Session B attempts insert into gap, expect 1205 timeout
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = b.prepareStatement("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES (3002,'ACC20249998',1,1000,1,0,101,NOW())")) { ps.executeUpdate(); }
                b.commit();
            }).isInstanceOf(Exception.class);

            a.rollback();
            b.rollback();
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：非唯一索引范围查询 → Next-Key Lock；范围外插入成功，范围内插入阻塞超时。
     * English: Non-unique range query → Next-Key Lock; insert outside succeeds, inside blocks and times out.
     */
    @Test
    @DisplayName("Lock-7B: Next-Key Lock on non-unique range; inserts outside vs inside")
    void nextKeyLockOnRange() throws Exception {
        seedBalances();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            b.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 2");

            // 中文：会话A范围锁定（5000~10000）
            // English: Session A locks range (5000~10000)
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE balance BETWEEN 5000 AND 10000 FOR UPDATE")) { ps.executeQuery(); }

            // 中文：范围外插入成功（balance=400）
            // English: Insert outside range succeeds (balance=400)
            try (PreparedStatement ps = b.prepareStatement("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES (3003,'ACC20240201',1,400,1,0,101,NOW())")) { ps.executeUpdate(); }
            b.commit();

            // 中文：范围内插入阻塞并超时（balance=6000）
            // English: Insert inside range blocks and times out (balance=6000)
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = b.prepareStatement("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES (3004,'ACC20240202',1,6000,1,0,101,NOW())")) { ps.executeUpdate(); }
                b.commit();
            }).isInstanceOf(Exception.class);

            a.rollback();
            b.rollback();
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：间隙锁不互斥；两个不同不存在值的 FOR UPDATE 可同时成功，但在该间隙的插入会被阻塞。
     * English: Gap locks are not mutually exclusive; two different non-existent value locks succeed, insert in gap blocks.
     */
    @Test
    @DisplayName("Lock-7C: Gap locks not mutually exclusive but block INSERT")
    void gapLocksNonMutualButBlockInsert() throws Exception {
        seedBalances();
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection(); Connection c = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            c.setAutoCommit(false);
            c.createStatement().execute("SET SESSION innodb_lock_wait_timeout = 2");

            // 中文：会话A锁定不存在值 8888 的间隙
            // English: Session A locks gap for non-existent value 8888
            try (PreparedStatement ps = a.prepareStatement("SELECT * FROM account_transaction WHERE balance = 8888 FOR UPDATE")) { ps.executeQuery(); }

            // 中文：会话B锁定同一间隙内另一个不存在值 9999（成功）
            // English: Session B locks another non-existent value 9999 in same gap (success)
            try (PreparedStatement ps = b.prepareStatement("SELECT * FROM account_transaction WHERE balance = 9999 FOR UPDATE")) { ps.executeQuery(); }

            // 中文：会话C尝试在该间隙插入，预期超时
            // English: Session C attempts insert into gap, expect timeout
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO account_transaction (user_id, account_no, account_type, balance, status, risk_level, branch_id, last_trans_time) VALUES (3005,'ACC20240203',1,10000,1,0,101,NOW())")) { ps.executeUpdate(); }
                c.commit();
            }).isInstanceOf(Exception.class);

            a.rollback();
            b.rollback();
            c.rollback();
        }
    }
}

