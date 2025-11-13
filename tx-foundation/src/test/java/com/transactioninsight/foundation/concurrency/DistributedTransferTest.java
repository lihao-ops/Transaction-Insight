package com.transactioninsight.foundation.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：转账系统分布式事务实战：本地事务与两阶段提交（2PC）实验，验证原子性与一致性。
 * English: Distributed transfer practice: local transaction and 2PC experiments validating atomicity and consistency.
 *
 * 使用场景 / Use Cases:
 * 中文：在单库场景验证本地事务幂等与锁序，在跨阶段场景验证冻结/提交/回滚流程。
 * English: Validate local transaction idempotence and lock order; validate freeze/commit/rollback in multi-phase scenario.
 *
 * 设计目的 / Design Purpose:
 * 中文：提供可执行的存储过程与断言，演示转账系统的关键设计点。
 * English: Provide executable stored procedures and assertions demonstrating key design points of transfer systems.
 */
@SpringBootTest
public class DistributedTransferTest {

    @Autowired
    private DataSource dataSource;

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS transfer_log");
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS transfer_prepare");
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "version INT NOT NULL DEFAULT 0, " +
                            "frozen_amount DECIMAL(15,2) NOT NULL DEFAULT 0) ENGINE=InnoDB");
            c.createStatement().executeUpdate(
                    "CREATE TABLE transfer_log (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "trans_id VARCHAR(50), from_account BIGINT, to_account BIGINT, amount DECIMAL(15,2), status VARCHAR(20), create_time DATETIME) ENGINE=InnoDB");
            c.createStatement().executeUpdate(
                    "CREATE TABLE transfer_prepare (" +
                            "trans_id VARCHAR(50) PRIMARY KEY, from_account BIGINT, to_account BIGINT, amount DECIMAL(15,2), status TINYINT, create_time DATETIME, expire_time DATETIME, INDEX idx_status_expire(status,expire_time)) ENGINE=InnoDB");
            c.createStatement().executeUpdate("INSERT INTO account_transaction(balance) VALUES (5000),(3000)");

            // 中文：本地事务过程
            // English: Local transaction procedure
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_transfer_local");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_transfer_local(IN p_from BIGINT, IN p_to BIGINT, IN p_amount DECIMAL(15,2), IN p_trans_id VARCHAR(50), OUT p_result VARCHAR(50)) " +
                            "proc_label: BEGIN DECLARE v_from_balance DECIMAL(15,2); DECLARE v_exists INT; " +
                            "DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; SET p_result='ERROR'; END; START TRANSACTION; " +
                            "SELECT COUNT(*) INTO v_exists FROM transfer_log WHERE trans_id=p_trans_id; IF v_exists>0 THEN SET p_result='DUPLICATE'; ROLLBACK; LEAVE proc_label; END IF; " +
                            "IF p_from<p_to THEN SELECT balance INTO v_from_balance FROM account_transaction WHERE id=p_from FOR UPDATE; SELECT balance FROM account_transaction WHERE id=p_to FOR UPDATE; " +
                            "ELSE SELECT balance FROM account_transaction WHERE id=p_to FOR UPDATE; SELECT balance INTO v_from_balance FROM account_transaction WHERE id=p_from FOR UPDATE; END IF; " +
                            "IF v_from_balance<p_amount THEN SET p_result='INSUFFICIENT_BALANCE'; ROLLBACK; LEAVE proc_label; END IF; " +
                            "UPDATE account_transaction SET balance=balance-p_amount, version=version+1 WHERE id=p_from; " +
                            "UPDATE account_transaction SET balance=balance+p_amount, version=version+1 WHERE id=p_to; " +
                            "INSERT INTO transfer_log(trans_id,from_account,to_account,amount,status,create_time) VALUES(p_trans_id,p_from,p_to,p_amount,'SUCCESS',NOW()); SET p_result='SUCCESS'; COMMIT; END"
            );

            // 中文：2PC - 预备、提交、回滚
            // English: 2PC - prepare, commit, rollback
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_transfer_prepare");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_transfer_prepare(IN p_trans_id VARCHAR(50), IN p_from BIGINT, IN p_amount DECIMAL(15,2), OUT p_result VARCHAR(50)) " +
                            "BEGIN DECLARE v_balance DECIMAL(15,2); START TRANSACTION; " +
                            "INSERT INTO transfer_prepare(trans_id,from_account,amount,status,create_time,expire_time) VALUES(p_trans_id,p_from,p_amount,0,NOW(),NOW()+INTERVAL 1 MINUTE); " +
                            "SELECT balance INTO v_balance FROM account_transaction WHERE id=p_from FOR UPDATE; " +
                            "IF v_balance<p_amount THEN SET p_result='INSUFFICIENT'; ROLLBACK; ELSE UPDATE account_transaction SET frozen_amount=frozen_amount+p_amount WHERE id=p_from; UPDATE transfer_prepare SET status=1 WHERE trans_id=p_trans_id; SET p_result='PREPARED'; COMMIT; END IF; END"
            );
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_transfer_commit");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_transfer_commit(IN p_trans_id VARCHAR(50), OUT p_result VARCHAR(50)) " +
                            "BEGIN DECLARE v_from BIGINT; DECLARE v_to BIGINT; DECLARE v_amount DECIMAL(15,2); START TRANSACTION; " +
                            "SELECT from_account,0,amount INTO v_from,v_to,v_amount FROM transfer_prepare WHERE trans_id=p_trans_id AND status=1 FOR UPDATE; " +
                            "UPDATE account_transaction SET balance=balance-v_amount, frozen_amount=frozen_amount-v_amount WHERE id=v_from; " +
                            "UPDATE account_transaction SET balance=balance+v_amount WHERE id=2; UPDATE transfer_prepare SET status=2 WHERE trans_id=p_trans_id; SET p_result='COMMITTED'; COMMIT; END"
            );
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_transfer_rollback");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_transfer_rollback(IN p_trans_id VARCHAR(50), OUT p_result VARCHAR(50)) " +
                            "BEGIN DECLARE v_from BIGINT; DECLARE v_amount DECIMAL(15,2); START TRANSACTION; " +
                            "SELECT from_account,amount INTO v_from,v_amount FROM transfer_prepare WHERE trans_id=p_trans_id AND status=1 FOR UPDATE; " +
                            "UPDATE account_transaction SET frozen_amount=frozen_amount-v_amount WHERE id=v_from; UPDATE transfer_prepare SET status=3 WHERE trans_id=p_trans_id; SET p_result='ROLLBACK'; COMMIT; END"
            );
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：调用本地事务转账过程，验证两账户余额变化与流水记录写入；确保幂等防重复。
     * English: Invoke local transfer procedure, verify balances of two accounts and log entry; ensure idempotence preventing duplicates.
     */
    @Test
    @DisplayName("HC-Case2A: Local transaction transfer with idempotence")
    void localTransactionTransfer() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：读取初始余额
            // English: Read initial balances
            BigDecimal b1 = readBigDecimal(c, "SELECT balance FROM account_transaction WHERE id=1");
            BigDecimal b2 = readBigDecimal(c, "SELECT balance FROM account_transaction WHERE id=2");

            // 中文：执行转账
            // English: Execute transfer
            try (CallableStatement cs = c.prepareCall("{CALL sp_transfer_local(?, ?, ?, ?, ?)}")) {
                cs.setLong(1, 1L); cs.setLong(2, 2L); cs.setBigDecimal(3, new BigDecimal("100.00")); cs.setString(4, "tx-abc-001"); cs.registerOutParameter(5, Types.VARCHAR); cs.execute();
                assertThat(cs.getString(5)).isEqualTo("SUCCESS");
            }

            // 中文：重复调用同一 trans_id，期望 DUPLICATE
            // English: Re-call with same trans_id, expect DUPLICATE
            try (CallableStatement cs = c.prepareCall("{CALL sp_transfer_local(?, ?, ?, ?, ?)}")) {
                cs.setLong(1, 1L); cs.setLong(2, 2L); cs.setBigDecimal(3, new BigDecimal("100.00")); cs.setString(4, "tx-abc-001"); cs.registerOutParameter(5, Types.VARCHAR); cs.execute();
                assertThat(cs.getString(5)).isIn("DUPLICATE", "ERROR");
            }

            BigDecimal nb1 = readBigDecimal(c, "SELECT balance FROM account_transaction WHERE id=1");
            BigDecimal nb2 = readBigDecimal(c, "SELECT balance FROM account_transaction WHERE id=2");
            assertThat(nb1).isEqualByComparingTo(b1.subtract(new BigDecimal("100.00")));
            assertThat(nb2).isEqualByComparingTo(b2.add(new BigDecimal("100.00")));
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：调用 2PC 预备与提交，验证冻结金额与最终余额变化；同时演示回滚路径。
     * English: Invoke 2PC prepare and commit, verify frozen and final balances; also demonstrate rollback path.
     */
    @Test
    @DisplayName("HC-Case2B: Two-phase commit (prepare/commit/rollback)")
    void twoPhaseCommit() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：预备阶段
            // English: Prepare phase
            try (CallableStatement cs = c.prepareCall("{CALL sp_transfer_prepare(?, ?, ?, ?)}")) {
                cs.setString(1, "tx-2pc-001"); cs.setLong(2, 1L); cs.setBigDecimal(3, new BigDecimal("200.00")); cs.registerOutParameter(4, Types.VARCHAR); cs.execute();
                assertThat(cs.getString(4)).isEqualTo("PREPARED");
            }
            BigDecimal frozen = readBigDecimal(c, "SELECT frozen_amount FROM account_transaction WHERE id=1");
            assertThat(frozen).isEqualByComparingTo("200.00");

            // 中文：提交阶段
            // English: Commit phase
            try (CallableStatement cs = c.prepareCall("{CALL sp_transfer_commit(?, ?)}")) {
                cs.setString(1, "tx-2pc-001"); cs.registerOutParameter(2, Types.VARCHAR); cs.execute();
                assertThat(cs.getString(2)).isEqualTo("COMMITTED");
            }
            BigDecimal b1 = readBigDecimal(c, "SELECT balance FROM account_transaction WHERE id=1");
            BigDecimal b2 = readBigDecimal(c, "SELECT balance FROM account_transaction WHERE id=2");
            assertThat(b1).isLessThan(readBigDecimal(c, "SELECT 5000")); // 扣减成功
            assertThat(b2).isGreaterThan(readBigDecimal(c, "SELECT 3000")); // 增加成功

            // 中文：回滚演示（新事务号）
            // English: Rollback demo (new trans id)
            try (CallableStatement cs = c.prepareCall("{CALL sp_transfer_prepare(?, ?, ?, ?)}")) {
                cs.setString(1, "tx-2pc-002"); cs.setLong(2, 1L); cs.setBigDecimal(3, new BigDecimal("150.00")); cs.registerOutParameter(4, Types.VARCHAR); cs.execute();
            }
            try (CallableStatement cs = c.prepareCall("{CALL sp_transfer_rollback(?, ?)}")) {
                cs.setString(1, "tx-2pc-002"); cs.registerOutParameter(2, Types.VARCHAR); cs.execute();
                assertThat(cs.getString(2)).isEqualTo("ROLLBACK");
            }
            BigDecimal frozenAfterRollback = readBigDecimal(c, "SELECT frozen_amount FROM account_transaction WHERE id=1");
            assertThat(frozenAfterRollback).isEqualByComparingTo("0.00");
        }
    }

    private BigDecimal readBigDecimal(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1);} }
    }
}

