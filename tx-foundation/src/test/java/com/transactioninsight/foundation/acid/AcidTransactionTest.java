package com.transactioninsight.foundation.acid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类说明 / Class Description:
 * 中文：基于 MySQL 的事务 ACID 验证实验集合，分别验证原子性、一致性、隔离性、持久性。
 * English: MySQL transaction ACID verification test suite validating Atomicity, Consistency, Isolation and Durability.
 *
 * 使用场景 / Use Cases:
 * 中文：在本地数据库中复刻核心事务性质，辅助教学与面试准备。
 * English: Reproduce core transactional properties in local DB for education and interview prep.
 *
 * 设计目的 / Design Purpose:
 * 中文：提供可执行、可断言的最小实验，强调失败路径与边界条件。
 * English: Provide executable, assertive minimal experiments emphasizing failure paths and boundaries.
 *
 * 执行前提 / Execution Prerequisites:
 * 中文：
 * 1) 本地 MySQL 可用，库 `transaction_study` 存在且数据源可连接；
 * 2) 具备在该库中 `CREATE TABLE / INSERT / UPDATE / DELETE` 的权限；
 * 3) InnoDB 引擎启用，支持会话级隔离设置（RC/RR）；
 * 4) Spring Boot 测试以 `@SpringBootTest` 运行且数据源指向本地 MySQL；
 * English:
 * 1) Local MySQL reachable, schema `transaction_study` exists and datasource connects;
 * 2) Privileges to `CREATE TABLE / INSERT / UPDATE / DELETE` in the schema;
 * 3) InnoDB engine enabled with session-level isolation (RC/RR);
 * 4) Spring Boot tests run via `@SpringBootTest` with datasource pointing to local MySQL.
 *
 * 实际执行 SQL / Executed SQL (按实验顺序) :
 * 中文：
 * [环境初始化]
 * - CREATE TABLE IF NOT EXISTS account_transaction (id BIGINT PRIMARY KEY AUTO_INCREMENT, balance DECIMAL(15,2) NOT NULL, version INT NOT NULL DEFAULT 0, updated_at DATETIME NULL) ENGINE=InnoDB
 * - DELETE FROM account_transaction
 * - INSERT INTO account_transaction (balance, version, updated_at) VALUES (1000.00, 0, NOW()), (2000.00, 0, NOW())
 *
 * [原子性]
 * - UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = 1   (900.00)
 * - UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = 2   (NULL → 触发 NOT NULL 异常)
 * - ROLLBACK
 * - SELECT balance FROM account_transaction WHERE id = 1
 * - SELECT balance FROM account_transaction WHERE id = 2
 *
 * [一致性]
 * - SELECT SUM(balance) FROM account_transaction
 * - SELECT balance FROM account_transaction WHERE id = 1
 * - SELECT balance FROM account_transaction WHERE id = 2
 * - UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = 1   (b1 - 300)
 * - UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = 2   (b2 + 300)
 * - COMMIT
 * - SELECT SUM(balance) FROM account_transaction
 *
 * [隔离性（RC）]
 * - SET SESSION transaction_isolation = 'READ-COMMITTED'  (sessionA)
 * - SET SESSION transaction_isolation = 'READ-COMMITTED'  (sessionB)
 * - SELECT balance FROM account_transaction WHERE id = 1   (sessionB)
 * - UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = 1   (sessionB: +123.45)
 * - SELECT balance FROM account_transaction WHERE id = 1   (sessionA: 提交前读旧值)
 * - COMMIT   (sessionB)
 * - SELECT balance FROM account_transaction WHERE id = 1   (sessionA: 提交后读新值)
 * - COMMIT   (sessionA)
 *
 * [持久性]
 * - SELECT balance FROM account_transaction WHERE id = 2
 * - UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = 2   (+77.77)
 * - COMMIT
 * - SELECT balance FROM account_transaction WHERE id = 2   (重连后读取)
 * English:
 * See the Chinese list above; each statement mirrors the actual JDBC prepared SQL executed in tests.
 */

@SpringBootTest
public class AcidTransactionTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(AcidTransactionTest.class);

    /**
     * 方法说明 / Method Description:
     * 中文：环境初始化（幂等），创建实验表并准备基线数据。
     * English: Environment init (idempotent), create lab table and seed baseline data.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行失败导致测试失败
     */
    private void init() throws Exception {
        try (Connection init = dataSource.getConnection()) {
            // 中文：创建实验表（如果不存在）
            // English: Create lab table if not exists
            init.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            // 中文：清空并重置两条记录，便于一致性与转账类实验
            // English: Reset to two rows for consistency/transfer experiments
            init.createStatement().executeUpdate("DELETE FROM account_transaction");
            init.createStatement().executeUpdate("INSERT INTO account_transaction (balance, version, updated_at) VALUES (1000.00, 0, NOW()), (2000.00, 0, NOW())");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：原子性（Atomicity）验证：事务内异常触发整体回滚，任何部分写入都不会生效。
     * English: Atomicity verification: exception in transaction triggers full rollback; no partial writes persist.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行异常或断言失败会使测试失败
     */
    @Test
    @DisplayName("ACID-Atomicity: rollback on exception leaves no partial writes")
    void atomicityRollbackOnFailure() throws Exception {
        init();
        try (Connection conn = dataSource.getConnection()) {
            // 中文：关闭自动提交以手动控制事务
            // English: Disable auto-commit to manually control transaction
            conn.setAutoCommit(false);

            // 中文：先执行第一步扣减
            // English: Perform first deduction
            updateBalance(conn, 1L, new BigDecimal("900.00"));

            // 中文：模拟异常（例如违反约束或主动抛出）
            // English: Simulate exception (constraint violation or manual throw)
            assertThatThrownBy(() -> updateBalance(conn, 2L, null)).isInstanceOf(Exception.class);

            // 中文：异常后回滚事务，保证两个更新都不生效
            // English: Roll back ensuring none of updates persist
            conn.rollback();

            // 中文：验证两条记录保持初始值
            // English: Verify both rows keep initial values
            assertThat(readBalance(conn, 1L)).isEqualByComparingTo("1000.00");
            assertThat(readBalance(conn, 2L)).isEqualByComparingTo("2000.00");
            log.info("实验成功：原子性验证通过；事务异常已整体回滚，未产生部分写入 / Success: Atomicity confirmed; transaction rolled back on error, no partial writes");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：一致性（Consistency）验证：跨两行的转账在提交后保持总余额守恒，出现错误时回滚保持不变量。
     * English: Consistency verification: cross-row transfer preserves total sum after commit; on error, rollback keeps invariant.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行异常或断言失败会使测试失败
     */
    @Test
    @DisplayName("ACID-Consistency: transfer preserves sum invariant")
    void consistencySumInvariant() throws Exception {
        init();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // 中文：初始总额
            // English: Initial sum
            BigDecimal sum0 = readSum(conn);

            // 中文：执行从 id=1 向 id=2 转账 300
            // English: Transfer 300 from id=1 to id=2
            BigDecimal b1 = readBalance(conn, 1L);
            BigDecimal b2 = readBalance(conn, 2L);
            updateBalance(conn, 1L, b1.subtract(new BigDecimal("300.00")));
            updateBalance(conn, 2L, b2.add(new BigDecimal("300.00")));

            // 中文：提交事务
            // English: Commit transaction
            conn.commit();

            // 中文：提交后总额应保持不变
            // English: Sum invariant must hold after commit
            BigDecimal sum1 = readSum(conn);
            assertThat(sum1).isEqualByComparingTo(sum0);
            log.info("实验成功：一致性验证通过；跨行转账后总额守恒 / Success: Consistency confirmed; cross-row transfer preserved total sum");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：隔离性（Isolation）验证：在 RC/RR 下，会话A不可读到会话B未提交写入；提交后视级别决定可见性。
     * English: Isolation verification: under RC/RR, Session A cannot read Session B's uncommitted write; after commit, visibility depends on level.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行异常或断言失败会使测试失败
     */
    @Test
    @DisplayName("ACID-Isolation: uncommitted changes are invisible (RC/RR)")
    void isolationUncommittedInvisible() throws Exception {
        init();
        try (Connection sessionA = dataSource.getConnection();
             Connection sessionB = dataSource.getConnection()) {
            sessionA.setAutoCommit(false);
            sessionB.setAutoCommit(false);
            // 中文：设置 RC 级别（RR 同理可验证快照行为）
            // English: Set RC level (RR similarly verifies snapshot behavior)
            sessionA.createStatement().execute("SET SESSION transaction_isolation = 'READ-COMMITTED'");
            sessionB.createStatement().execute("SET SESSION transaction_isolation = 'READ-COMMITTED'");

            // 中文：会话B未提交写入
            // English: Session B uncommitted write
            BigDecimal b1 = readBalance(sessionB, 1L);
            updateBalance(sessionB, 1L, b1.add(new BigDecimal("123.45")));

            // 中文：会话A读取看不到未提交数据（仍为旧值）
            // English: Session A cannot see uncommitted data (still old value)
            BigDecimal aReadBeforeCommit = readBalance(sessionA, 1L);
            assertThat(aReadBeforeCommit).isEqualByComparingTo(b1);

            // 中文：提交会话B后，RC 下会话A再次读取可见新值
            // English: After Session B commit, RC makes new value visible to Session A
            sessionB.commit();
            BigDecimal aReadAfterCommit = readBalance(sessionA, 1L);
            assertThat(aReadAfterCommit).isEqualByComparingTo(b1.add(new BigDecimal("123.45")));

            sessionA.commit();
            log.info("实验成功：隔离性验证通过；未提交写入不可见，提交后 RC 可见 / Success: Isolation confirmed; uncommitted write invisible, visible after commit under RC");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：持久性（Durability）验证：提交后断开连接重新读取仍能得到已提交的结果。
     * English: Durability verification: after commit, reconnect and read to get the committed result.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: SQL 执行异常或断言失败会使测试失败
     */
    @Test
    @DisplayName("ACID-Durability: committed data survives reconnect")
    void durabilityCommittedPersists() throws Exception {
        init();
        BigDecimal committedValue;
        // 中文：第一次连接，更新并提交
        // English: First connect, update and commit
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            BigDecimal b2 = readBalance(conn, 2L);
            updateBalance(conn, 2L, b2.add(new BigDecimal("77.77")));
            conn.commit();
            committedValue = b2.add(new BigDecimal("77.77"));
        }
        // 中文：重新连接并读取，验证已提交数据仍存在
        // English: Reconnect and read to verify committed data persists
        try (Connection conn2 = dataSource.getConnection()) {
            BigDecimal readBack = readBalance(conn2, 2L);
            assertThat(readBack).isEqualByComparingTo(committedValue);
            log.info("实验成功：持久性验证通过；提交后重连读取到已提交数据 / Success: Durability confirmed; committed data persisted across reconnect");
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：读取指定行余额。
     * English: Read balance of the specified row.
     *
     * 参数 / Parameters:
     * @param conn 中文：连接 / English: Connection
     * @param id   中文：主键ID / English: Primary key ID
     *
     * 返回值 / Return:
     * 中文：余额 / English: Balance
     *
     * 异常 / Exceptions:
     * 中文/英文：查询失败抛出异常
     */
    private BigDecimal readBalance(Connection conn, long id) throws Exception {
        // 中文：执行查询以获取余额
        // English: Execute query to fetch balance
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account_transaction WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getBigDecimal(1); }
            throw new IllegalStateException("Row not found: id=" + id);
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：更新指定行余额（允许传入 null 以触发异常路径）。
     * English: Update balance for specified row (allows null to trigger failure path).
     *
     * 参数 / Parameters:
     * @param conn   中文：连接 / English: Connection
     * @param id     中文：主键ID / English: Primary key ID
     * @param amount 中文：新余额 / English: New balance
     *
     * 返回值 / Return: 无
     * 异常 / Exceptions: 更新失败抛出异常
     */
    private void updateBalance(Connection conn, long id, BigDecimal amount) throws Exception {
        // 中文：执行更新，向失败路径传递 null 以制造异常
        // English: Execute update, pass null to create failure path
        try (PreparedStatement ps = conn.prepareStatement("UPDATE account_transaction SET balance = ?, updated_at = NOW() WHERE id = ?")) {
            if (amount == null) {
                ps.setBigDecimal(1, null); // will raise ERROR (NOT NULL)
            } else {
                ps.setBigDecimal(1, amount);
            }
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：计算表中两行余额的总和。
     * English: Compute the sum of balances for two rows in table.
     *
     * 参数 / Parameters:
     * @param conn 中文：连接 / English: Connection
     *
     * 返回值 / Return:
     * 中文：总余额 / English: Total balance
     *
     * 异常 / Exceptions:
     * 中文/英文：查询失败抛出异常
     */
    private BigDecimal readSum(Connection conn) throws Exception {
        // 中文：统计全部余额并返回
        // English: Sum all balances and return
        try (PreparedStatement ps = conn.prepareStatement("SELECT SUM(balance) FROM account_transaction")) {
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1); }
        }
    }
}
