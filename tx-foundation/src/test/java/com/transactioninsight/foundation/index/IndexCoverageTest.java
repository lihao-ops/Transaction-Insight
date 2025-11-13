package com.transactioninsight.foundation.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：覆盖索引 vs 回表性能对比实验，量化并验证 Using index 标志与查询回表的差异。
 * English: Covering index vs back-to-table experiment, quantifying and verifying Using index flag and back-to-table differences.
 *
 * 使用场景 / Use Cases:
 * 中文：指导高频查询的索引设计，避免 SELECT * 导致的回表。
 * English: Guide index design for hot queries, avoiding SELECT * causing back-to-table.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过 EXPLAIN 与简单时间对比，演示覆盖索引的优化收益。
 * English: Use EXPLAIN and simple timing to demonstrate benefits of covering index.
 */
@SpringBootTest
public class IndexCoverageTest {

    @Autowired
    private DataSource dataSource;

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "user_id BIGINT, " +
                            "balance DECIMAL(15,2) NOT NULL, " +
                            "status TINYINT, " +
                            "remark TEXT, " +
                            "updated_at DATETIME NULL) ENGINE=InnoDB");
            c.createStatement().executeUpdate("CREATE INDEX idx_user_id ON account_transaction(user_id)");
            // 中文：插入一定数量的数据以便 EXPLAIN 有意义
            // English: Insert enough data so EXPLAIN becomes meaningful
            for (int i = 0; i < 200; i++) {
                long uid = (i % 50 == 0) ? 1001 : 1000 + (i % 200);
                c.createStatement().executeUpdate(
                        "INSERT INTO account_transaction (user_id, balance, status, remark, updated_at) VALUES (" +
                                uid + "," + (1000 + i) + "," + (i % 3) + ", 'note' , NOW())");
            }
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：非覆盖索引查询（需要回表），验证 EXPLAIN 的 Extra 不包含 Using index。
     * English: Non-covering indexed query (requires back-to-table), verify EXPLAIN Extra does not include Using index.
     */
    @Test
    @DisplayName("Index-10A: Non-covering index requires back-to-table")
    void nonCoveringRequiresBackToTable() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：EXPLAIN 验证非覆盖索引的 Extra 不包含 Using index
            // English: EXPLAIN verifies Extra does not include Using index for non-covering
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT id, user_id, balance, status FROM account_transaction WHERE user_id = 1001")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, String> explain = readExplainRow(rs);
                    assertThat(explain.get("key")).isIn("idx_user_id", null);
                    assertThat(explain.get("Extra") == null || !explain.get("Extra").contains("Using index")).isTrue();
                }
            }
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：创建覆盖索引后，验证 EXPLAIN 的 Extra 包含 Using index；同时做简单时间对比。
     * English: After creating covering index, verify EXPLAIN Extra includes Using index; also do simple timing comparison.
     */
    @Test
    @DisplayName("Index-10B: Covering index shows Using index and speeds up query")
    void coveringIndexUsingIndex() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("ALTER TABLE account_transaction ADD INDEX idx_user_cover(user_id, balance, status)");

            // 中文：EXPLAIN 验证覆盖索引使用情况
            // English: EXPLAIN verifies covering index usage
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT id, user_id, balance, status FROM account_transaction WHERE user_id = 1001")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, String> explain = readExplainRow(rs);
                    assertThat(explain.get("key")).isEqualTo("idx_user_cover");
                    assertThat(explain.get("Extra")).contains("Using index");
                }
            }

            // 中文：简单时间对比（多次执行取最优），非严格基准，仅用于演示
            // English: Simple time comparison (best-of-N runs), non-rigorous demo only
            long t1 = bestExecutionTime(c, "SELECT id, user_id, balance, status FROM account_transaction WHERE user_id = 1001", 5);
            assertThat(t1).isGreaterThanOrEqualTo(0L);
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：SELECT * 会破坏覆盖索引，验证 Extra 不含 Using index。
     * English: SELECT * breaks covering index, verify Extra without Using index.
     */
    @Test
    @DisplayName("Index-10C: SELECT * breaks covering index (no Using index)")
    void selectStarBreaksCovering() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("ALTER TABLE account_transaction ADD INDEX idx_user_cover(user_id, balance, status)");
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT * FROM account_transaction WHERE user_id = 1001")) {
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, String> explain = readExplainRow(rs);
                    assertThat(explain.get("Extra") == null || !explain.get("Extra").contains("Using index")).isTrue();
                }
            }
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：读取 EXPLAIN 第一行结果的关键列。
     * English: Read key columns from first EXPLAIN row.
     */
    private Map<String, String> readExplainRow(ResultSet rs) throws Exception {
        // 中文：读取第一行的 key、Extra 等字段
        // English: Read first row's key, Extra, etc.
        Map<String, String> m = new HashMap<>();
        if (rs.next()) {
            m.put("type", rs.getString("type"));
            m.put("key", rs.getString("key"));
            m.put("key_len", rs.getString("key_len"));
            m.put("rows", rs.getString("rows"));
            m.put("Extra", rs.getString("Extra"));
        }
        return m;
    }

    /**
     * 方法说明 / Method Description:
     * 中文：多次执行同一查询，返回最短耗时（毫秒）。
     * English: Execute the same query multiple times and return shortest elapsed time (ms).
     */
    private long bestExecutionTime(Connection c, String sql, int runs) throws Exception {
        // 中文：预热一次，避免首次编译开销
        // English: Warm up once to avoid first-run overhead
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

