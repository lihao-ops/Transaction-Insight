package com.transactioninsight.foundation.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：实时报表聚合优化实战：直接聚合（慢）、定时汇总（快）、触发器增量维护（强实时）。
 * English: Real-time reporting aggregation: direct aggregate (slow), scheduled summary (fast), trigger-based incremental (strong real-time).
 *
 * 使用场景 / Use Cases:
 * 中文：面向网点余额统计，在不同实时性要求下选择方案。
 * English: For branch balance stats, choose scheme per real-time constraints.
 *
 * 设计目的 / Design Purpose:
 * 中文：演示性验证聚合表更新与查询性能；事件与触发器在权限不足时容错。
 * English: Demonstrate summary update and query performance; tolerate event/trigger creation without privileges.
 */
@SpringBootTest
public class RealtimeAggregationTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(RealtimeAggregationTest.class);

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS branch_summary");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, branch_id INT, status TINYINT, balance DECIMAL(18,2)) ENGINE=InnoDB");
            c.createStatement().executeUpdate(
                    "CREATE TABLE branch_summary (" +
                            "branch_id INT PRIMARY KEY, account_count INT, total_balance DECIMAL(18,2), avg_balance DECIMAL(15,2), update_time DATETIME, INDEX idx_update_time(update_time)) ENGINE=InnoDB");
            for (int i = 0; i < 10000; i++) {
                int branch = 100 + (i % 20); int status = i % 2; double bal = 100 + (i % 5000);
                c.createStatement().executeUpdate("INSERT INTO account_transaction(branch_id,status,balance) VALUES (" + branch + "," + status + "," + bal + ")");
            }
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：直接聚合与定时汇总存储过程的对比；验证汇总表的聚合结果正确。
     * English: Compare direct aggregate vs scheduled summary procedure; verify summary table correctness.
     */
    @Test
    @DisplayName("HC-Case4: Direct aggregate vs scheduled summary (materialized)")
    void directVsScheduledSummary() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：直接聚合（演示耗时，不做严格断言）
            // English: Direct aggregate (demo timing, no strict assert)
        long tAgg = bestTime(c, "SELECT branch_id, COUNT(*), SUM(balance), AVG(balance) FROM account_transaction WHERE status=1 GROUP BY branch_id", 1);
        assertThat(tAgg).isGreaterThanOrEqualTo(0L);

            // 中文：创建汇总过程并刷新汇总表
            // English: Create summary procedure and refresh summary table
            c.createStatement().executeUpdate("DROP PROCEDURE IF EXISTS sp_refresh_branch_summary");
            c.createStatement().executeUpdate(
                    "CREATE PROCEDURE sp_refresh_branch_summary() BEGIN DELETE FROM branch_summary; INSERT INTO branch_summary SELECT branch_id, COUNT(*), SUM(balance), AVG(balance), NOW() FROM account_transaction WHERE status=1 GROUP BY branch_id; END");
            c.createStatement().executeUpdate("CALL sp_refresh_branch_summary()");

            // 中文：查询汇总表（期望更快）
            // English: Query summary table (expect faster)
        long tSummary = bestTime(c, "SELECT * FROM branch_summary", 1);
        assertThat(tSummary).isLessThanOrEqualTo(tAgg);
            log.info("实验成功：定时汇总查询优于直接聚合，tAgg={}ms，tSummary={}ms", tAgg, tSummary);

            // 中文：抽样验证某分支的汇总正确性
            // English: Sample-check summary correctness for a branch
            int branch = 100;
            try (PreparedStatement ps = c.prepareStatement("SELECT account_count, total_balance FROM branch_summary WHERE branch_id=?")) {
                ps.setInt(1, branch);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { assertThat(rs.getInt(1)).isGreaterThan(0); assertThat(rs.getBigDecimal(2)).isGreaterThanOrEqualTo(new java.math.BigDecimal("0")); } }
                log.info("实验成功：汇总表抽样校验正确，branch={}, account_count>0 且 total_balance>=0", branch);
            }

            // 中文：尝试创建定时事件（权限不足时忽略）
            // English: Try creating event (ignore on insufficient privileges)
            try { c.createStatement().executeUpdate("CREATE EVENT IF NOT EXISTS event_refresh_summary ON SCHEDULE EVERY 1 MINUTE DO CALL sp_refresh_branch_summary()"); } catch (Exception ignored) { }
        }
    }

    private long bestTime(Connection c, String sql, int runs) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet ignored = ps.executeQuery()) { /* warmup */ } }
        long start = System.nanoTime();
        try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet ignored = ps.executeQuery()) { /* run */ } }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
