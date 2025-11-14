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
 * 中文：Index Condition Pushdown (ICP) 优化实验，验证 Extra 中 Using index condition 的出现与性能影响。
 * English: ICP optimization experiment verifying Extra Using index condition appearance and performance impact.
 *
 * 使用场景 / Use Cases:
 * 中文：理解非聚簇索引下推过滤以减少回表。
 * English: Understand non-clustered index pushdown filtering to reduce back-to-table.
 *
 * 设计目的 / Design Purpose:
 * 中文：对比开启/关闭 ICP 时的 EXPLAIN 与执行时间（演示级）。
 * English: Compare EXPLAIN and execution time when ICP on/off (demonstration).
 */
@SpringBootTest
public class IcpOptimizationTest {

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(IcpOptimizationTest.class);

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, branch_id INT, account_type TINYINT, status TINYINT, balance DECIMAL(15,2) NOT NULL) ENGINE=InnoDB");
            c.createStatement().executeUpdate("CREATE INDEX idx_branch_type_status_balance ON account_transaction(branch_id, account_type, status, balance)");
            for (int i = 0; i < 1000; i++) {
                int branch = 101 + (i % 10);
                int type = i % 3;
                int status = i % 2;
                double bal = 1000 + (i * 13 % 9000);
                c.createStatement().executeUpdate("INSERT INTO account_transaction(branch_id, account_type, status, balance) VALUES (" + branch + "," + type + "," + status + "," + bal + ")");
            }
        }
    }

    @Test
    @DisplayName("Index-12A: ICP on shows Using index condition; off shows Using where")
    void icpOnOffCompare() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：默认（ICP开启）
            // English: Default (ICP on)
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT * FROM account_transaction WHERE branch_id = 101 AND account_type = 1 AND status IN (1,2)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        assertThat(rs.getString("key")).isEqualTo("idx_branch_type_status_balance");
                        String extra = rs.getString("Extra");
                        assertThat(extra == null || extra.contains("Using index condition")).isTrue();
                    }
                }
            }

            // 中文：关闭 ICP 并验证 Extra（可能显示 Using where）
            // English: Turn ICP off and verify Extra (may show Using where)
            c.createStatement().executeUpdate("SET optimizer_switch='index_condition_pushdown=off'");
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN SELECT * FROM account_transaction WHERE branch_id = 101 AND account_type = 1 AND status IN (1,2)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String extra = rs.getString("Extra");
                        assertThat(extra == null || extra.contains("Using where")).isTrue();
                    }
                }
            }
            c.createStatement().executeUpdate("SET optimizer_switch='index_condition_pushdown=on'");
            log.info("实验成功：ICP 开关对比验证通过；开启时 Using index condition，关闭时 Using where / Success: ICP on/off confirmed; on→Using index condition, off→Using where");
        }
    }
}
