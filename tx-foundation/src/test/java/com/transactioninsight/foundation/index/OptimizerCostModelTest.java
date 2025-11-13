package com.transactioninsight.foundation.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 中文：查询优化器 Cost Model 分析实验，基于统计信息与 EXPLAIN FORMAT=JSON / optimizer_trace 观察计划选择与成本。
 * English: Optimizer Cost Model analysis experiment, observing plan choice and costs via statistics and EXPLAIN FORMAT=JSON / optimizer_trace.
 *
 * 使用场景 / Use Cases:
 * 中文：理解优化器索引选择与成本估算，辅助 Hint 与索引调整。
 * English: Understand optimizer index choice and cost estimation, aiding hints and index adjustments.
 *
 * 设计目的 / Design Purpose:
 * 中文：收集基数、选择性与成本信息，展示优化器决策依据。
 * English: Collect cardinality, selectivity and cost info to show optimizer decisions.
 */
@SpringBootTest
public class OptimizerCostModelTest {

    @Autowired
    private DataSource dataSource;

    private void seed() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.createStatement().executeUpdate("DROP TABLE IF EXISTS account_transaction");
            c.createStatement().executeUpdate(
                    "CREATE TABLE account_transaction (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT, status TINYINT, balance DECIMAL(15,2) NOT NULL, last_trans_time DATETIME, remark TEXT) ENGINE=InnoDB");
            c.createStatement().executeUpdate("CREATE INDEX idx_status ON account_transaction(status)");
            c.createStatement().executeUpdate("CREATE INDEX idx_balance_status ON account_transaction(balance, status)");
            c.createStatement().executeUpdate("CREATE INDEX idx_status_balance_time ON account_transaction(status, balance, last_trans_time)");
            for (int i = 0; i < 1000; i++) {
                int status = (i % 10 == 0) ? 1 : (i % 4); // 降低选择性
                double bal = 1000 + (i * 7 % 9000);
                c.createStatement().executeUpdate("INSERT INTO account_transaction(status,balance,last_trans_time,remark) VALUES (" + status + "," + bal + ", NOW(), 'r')");
            }
            c.createStatement().executeUpdate("ANALYZE TABLE account_transaction");
        }
    }

    @Test
    @DisplayName("Index-13A: EXPLAIN FORMAT=JSON shows cost_info; optimizer_trace lists considered plans")
    void explainJsonAndOptimizerTrace() throws Exception {
        seed();
        try (Connection c = dataSource.getConnection()) {
            // 中文：EXPLAIN FORMAT=JSON
            // English: EXPLAIN FORMAT=JSON
            try (PreparedStatement ps = c.prepareStatement("EXPLAIN FORMAT=JSON SELECT * FROM account_transaction WHERE status = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString(1);
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(json);
                        assertThat(root.toString()).contains("cost_info");
                    }
                }
            }

            // 中文：启用 optimizer_trace 并读取信息
            // English: Enable optimizer_trace and read info
            c.createStatement().executeUpdate("SET optimizer_trace='enabled=on' ");
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM account_transaction WHERE status = 1 AND balance > 5000")) { try (ResultSet ignored = ps.executeQuery()) { /* run */ } }
            try (PreparedStatement ps = c.prepareStatement("SELECT TRACE FROM information_schema.OPTIMIZER_TRACE")) {
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) assertThat(rs.getString(1)).contains("considered_execution_plans"); }
            }
            c.createStatement().executeUpdate("SET optimizer_trace='enabled=off' ");
        }
    }
}

