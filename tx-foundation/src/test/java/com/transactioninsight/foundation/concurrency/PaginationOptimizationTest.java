package com.transactioninsight.foundation.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类说明 / Class Description:
 * 中文：订单系统分页查询优化实战：传统 LIMIT、子查询、延迟关联与游标分页的性能对比（演示级）。
 * English: Order system pagination optimization: compare traditional LIMIT, subquery, delayed association, and cursor pagination (demo-level).
 *
 * 使用场景 / Use Cases:
 * 中文：展示深度分页下的性能退化与优化路径，适用于大数据量顺序翻页。
 * English: Show performance degradation on deep pagination and optimization paths for large sequential paging.
 *
 * 设计目的 / Design Purpose:
 * 中文：构造 5 万行演示数据，使用 EXPLAIN 与简单耗时对比验证优化效果。
 * English: Build 50k demo rows, use EXPLAIN and simple timing comparison to validate optimization.
 */
@SpringBootTest
public class PaginationOptimizationTest {

    @Autowired
    private DataSource dataSource;

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS transaction_log");
            c.createStatement().executeUpdate(
                    "CREATE TABLE transaction_log (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                            "account_id BIGINT, amount DECIMAL(15,2), trans_time DATETIME, INDEX idx_account_time(account_id, trans_time)) ENGINE=InnoDB");
            // 中文：插入演示数据（数量可调）
            // English: Insert demo data (tunable count)
            for (int i = 0; i < 50000; i++) {
                long acc = 1L;
                double amt = (i % 100) * 1.0;
                c.createStatement().executeUpdate("INSERT INTO transaction_log(account_id,amount,trans_time) VALUES (" + acc + "," + amt + ", NOW() - INTERVAL " + (i % 10000) + " SECOND)");
            }
        }
    }

    /**
     * 方法说明 / Method Description:
     * 中文：深度分页对比：传统 LIMIT vs 子查询与游标分页。
     * English: Deep pagination comparison: traditional LIMIT vs subquery and cursor pagination.
     */
    @Test
    @DisplayName("HC-Case3: Deep pagination optimization (subquery & cursor)")
    void deepPaginationOptimization() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：传统 LIMIT 深度分页（示例：第1000页）
            // English: Traditional LIMIT deep page (e.g., page 1000)
            String qLimit = "SELECT * FROM transaction_log WHERE account_id=1 ORDER BY trans_time DESC LIMIT 20000, 20";
            long tLimit = bestTime(c, qLimit, 2);

            // 中文：子查询优化：先用覆盖索引定位 id，再回表
            // English: Subquery: locate ids via covering index, then back-to-table
            String qSub = "SELECT t.* FROM transaction_log t INNER JOIN (SELECT id FROM transaction_log WHERE account_id=1 ORDER BY trans_time DESC LIMIT 20000,20) tmp ON t.id=tmp.id";
            long tSub = bestTime(c, qSub, 2);

            // 中文：游标分页：按最后一条记录继续分页（模拟）
            // English: Cursor: continue from last row (simulated)
            // 中文：先取第一页获取游标
            // English: Fetch first page to get cursor
            long lastId = readLong(c, "SELECT id FROM transaction_log WHERE account_id=1 ORDER BY trans_time DESC LIMIT 19,1");
            String qCursor = "SELECT id, trans_time, amount FROM transaction_log WHERE account_id=1 AND id <= " + lastId + " ORDER BY trans_time DESC, id DESC LIMIT 20";
            long tCursor = bestTime(c, qCursor, 2);

            // 中文：断言优化效果（演示级）：子查询/游标不慢于传统 LIMIT 深度分页
            // English: Assert improvements (demo-level): subquery/cursor not slower than traditional deep LIMIT
            assertThat(tSub).isLessThanOrEqualTo(tLimit);
            assertThat(tCursor).isLessThanOrEqualTo(tLimit);
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

    private long readLong(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1);} }
    }
}

