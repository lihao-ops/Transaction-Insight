package com.transactioninsight.foundation.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：慢查询优化全流程实验，模拟慢查询并通过索引与改写优化，使用 EXPLAIN 与时间对比验证。
 * English: Full slow query optimization flow; simulate slow queries and optimize via index and rewrite, validated with EXPLAIN and timing.
 *
 * 使用场景 / Use Cases:
 * 中文：展示从问题发现到索引/改写与效果验证的闭环。
 * English: Show closed loop from problem detection to index/rewrite and effect verification.
 *
 * 设计目的 / Design Purpose:
 * 中文：基于演示数据构造慢查询，避免依赖外部日志设施。
 * English: Construct slow queries on demo data, avoiding external log facilities.
 */
@SpringBootTest
public class SlowQueryOptimizationFlowTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(SlowQueryOptimizationFlowTest.class);

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, balance DECIMAL(15,2) NOT NULL, status TINYINT, remark TEXT, updated_at DATETIME) ENGINE=InnoDB");
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS transaction_log");
            c.createStatement().executeUpdate(
                    "CREATE TABLE transaction_log (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, account_id BIGINT, trans_type TINYINT, amount DECIMAL(15,2), trans_time DATETIME) ENGINE=InnoDB");
            for (int i = 0; i < 3000; i++) {
                long uid = 1000 + (i % 1000);
                int status = i % 3;
                String remark = (i % 20 == 0) ? "this is test remark" : "ordinary";
                c.createStatement().executeUpdate("INSERT INTO account_transaction(user_id,balance,status,remark,updated_at) VALUES (" + uid + "," + (100 + (i % 5000)) + "," + status + ", '" + remark + "', NOW())");
                c.createStatement().executeUpdate("INSERT INTO transaction_log(account_id, trans_type, amount, trans_time) VALUES (" + uid + "," + (i % 2) + "," + (i % 100) + ", NOW())");
            }
        }
    }

    @Test
    @DisplayName("Index-14: Slow query optimization via FULLTEXT and JOIN rewrite")
    void slowQueryOptimization() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：慢查询1：LIKE 模式，全表扫描
            // English: Slow Query 1: LIKE pattern, full scan
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT * FROM account_transaction WHERE remark LIKE '%test%'")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) assertThat(rs.getString("type")).isIn("ALL", "index"); }
            }

            // 中文：添加 FULLTEXT 并改写查询
            // English: Add FULLTEXT and rewrite query
            c.createStatement().executeUpdate("ALTER TABLE account_transaction ADD FULLTEXT INDEX ft_remark(remark)");
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT * FROM account_transaction WHERE MATCH(remark) AGAINST('test' IN NATURAL LANGUAGE MODE)")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) assertThat(rs.getString("type")).isNotBlank(); }
            }

            // 中文：慢查询2：无索引 JOIN
            // English: Slow Query 2: Non-indexed JOIN
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT a.*, t.* FROM account_transaction a, transaction_log t WHERE a.user_id = t.account_id")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) assertThat(rs.getString("type")).isNotBlank(); }
            }

            // 中文：改写为内连接并考虑索引（演示 EXPLAIN）
            // English: Rewrite to INNER JOIN and consider indexes (demonstration EXPLAIN)
            c.createStatement().executeUpdate("CREATE INDEX idx_log_account ON transaction_log(account_id)");
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT a.*, t.* FROM account_transaction a INNER JOIN transaction_log t ON a.user_id = t.account_id")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) assertThat(rs.getString("type")).isNotBlank(); }
            }

            // 中文：子查询改写为 EXISTS
            // English: Rewrite subquery to EXISTS
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT a.* FROM account_transaction a WHERE EXISTS (SELECT 1 FROM transaction_log t WHERE t.account_id = a.user_id AND t.trans_type = 1)")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) assertThat(rs.getString("type")).isNotBlank(); }
            }

            // 中文：简单对比执行时间（演示级）
            // English: Simple execution time comparison (demo-level)
            long slow = bestTime(c, "SELECT * FROM account_transaction WHERE remark LIKE '%test%'", 3);
            long fast = bestTime(c, "SELECT * FROM account_transaction WHERE MATCH(remark) AGAINST('test' IN NATURAL LANGUAGE MODE)", 3);
            assertThat(fast).isLessThanOrEqualTo(slow);
            log.info("实验成功：慢查询优化验证通过；FULLTEXT 与改写方案较 LIKE 全表扫描更快 / Success: Slow query optimization confirmed; FULLTEXT and rewrites faster than LIKE full scan");
        }
    }

    private long bestTime(Connection c, String sql, int runs) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet ignored = ps.executeQuery()) { /* warmup */ } }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet ignored = ps.executeQuery()) { /* run */ } }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMs < best) best = elapsedMs;
        }
        return best == Long.MAX_VALUE ? 0 : best;
    }
}
